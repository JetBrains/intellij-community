/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ConvertToBasicLatinAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    final Pair<PsiElement, Handler> pair = findHandler(element);
    if (pair == null) return false;

    final String text = pair.first.getText();
    for (int i = 0; i < text.length(); i++) {
      if (shouldConvert(text.charAt(i))) return true;
    }

    return false;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.convert.to.basic.latin");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return;

    final Pair<PsiElement, Handler> pair = findHandler(element);
    if (pair == null) return;

    final String text = pair.first.getText();
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      final char ch = text.charAt(i);
      if (!shouldConvert(ch)) {
        sb.append(ch);
      }
      else {
        pair.second.convert(sb, ch);
      }
    }

    pair.first.replace(pair.second.createReplacement(project, sb.toString(), pair.first.getParent()));
  }

  @Nullable
  private static Pair<PsiElement, Handler> findHandler(final PsiElement element) {
    for (final Handler handler : ourHandlers) {
      final PsiElement applicable = handler.findApplicable(element);
      if (applicable != null) {
        return Pair.create(applicable, handler);
      }
    }

    return null;
  }

  private static boolean shouldConvert(final char ch) {
    return Character.UnicodeBlock.of(ch) != Character.UnicodeBlock.BASIC_LATIN;
  }

  private interface Handler {
    @Nullable
    PsiElement findApplicable(PsiElement element);
    PsiElement createReplacement(Project project, String text, PsiElement context);
    void convert(StringBuilder sb, char ch);
  }

  private static final List<? extends Handler> ourHandlers = Arrays.asList(
    new Handler() {
      public PsiElement findApplicable(final PsiElement element) {
        return element instanceof PsiJavaToken && ourLiterals.contains(((PsiJavaToken)element).getTokenType()) ?
               element.getParent() : null;
      }

      public PsiElement createReplacement(final Project project, final String text, final PsiElement context) {
        return JavaPsiFacade.getElementFactory(project).createExpressionFromText(text, context);
      }

      public void convert(final StringBuilder sb, final char ch) {
        sb.append(String.format("\\u%04x", (int)ch));
      }
    },

    new Handler() {
      public PsiElement findApplicable(final PsiElement element) {
        loadEntities(element.getProject());

        if (element instanceof PsiDocComment) return element;
        if (element instanceof PsiDocToken) return element.getParent();
        if (element instanceof PsiWhiteSpace) return PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
        return null;
      }

      public PsiElement createReplacement(final Project project, final String text, final PsiElement context) {
        return JavaPsiFacade.getElementFactory(project).createDocCommentFromText(text, context);
      }

      public void convert(final StringBuilder sb, final char ch) {
        convertHtmlChar(sb, ch);
      }
    },

    new Handler() {
      public PsiElement findApplicable(final PsiElement element) {
        loadEntities(element.getProject());

        if (element instanceof PsiComment) return element;
        if (element instanceof PsiWhiteSpace) return PsiTreeUtil.getParentOfType(element, PsiComment.class);
        return null;
      }

      public PsiElement createReplacement(final Project project, final String text, final PsiElement context) {
        return JavaPsiFacade.getElementFactory(project).createCommentFromText(text, context);
      }

      public void convert(final StringBuilder sb, final char ch) {
        convertHtmlChar(sb, ch);
      }
    }
  );

  private static final TokenSet ourLiterals = TokenSet.create(JavaTokenType.CHARACTER_LITERAL, JavaTokenType.STRING_LITERAL);

  private static void convertHtmlChar(final StringBuilder sb, final char ch) {
    assert ourEntities != null;
    final String entity = ourEntities.get(ch);
    if (entity != null) {
      sb.append('&').append(entity).append(';');
    }
    else {
      sb.append("&#x").append(Integer.toHexString(ch)).append(';');
    }
  }

  private static Map<Character, String> ourEntities = null;

  private static void loadEntities(final Project project) {
    if (ourEntities != null) return;

    final String resource = ExternalResourceManager.getInstance().getResourceLocation(XmlUtil.HTML4_LOOSE_URI, project);
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(VfsUtil.urlToPath(VfsUtil.fixURLforIDEA(resource)));
    assert vFile != null;
    final PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    assert file instanceof XmlFile;

    final Pattern pattern = Pattern.compile("&#(\\d+);");
    final Map<Character, String> entities = new HashMap<Character, String>();
    XmlUtil.processXmlElements((XmlFile)file, new PsiElementProcessor() {
      public boolean execute(PsiElement element) {
        if (element instanceof XmlEntityDecl) {
          final XmlEntityDecl entity = (XmlEntityDecl)element;
          final Matcher m = pattern.matcher(entity.getValueElement().getValue());
          if (m.matches()) {
            final char i = (char)Integer.parseInt(m.group(1));
            if (shouldConvert(i)) {
              entities.put(i, entity.getName());
            }
          }
        }
        return true;
      }
    }, true);

    ourEntities = entities;
  }
}
