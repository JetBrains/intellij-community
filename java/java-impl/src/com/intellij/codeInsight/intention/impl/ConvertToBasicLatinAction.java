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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ConvertToBasicLatinAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ConvertToBasicLatinAction");

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    System.out.println("ConvertToBasicLatinAction.isAvailable");
    System.out.println("element = " + element);
    final Pair<PsiElement, Handler> pair = findHandler(element);
    System.out.println("pair = " + pair);
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
    System.out.println("ConvertToBasicLatinAction.invoke");
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    System.out.println("element = " + element);
    if (element == null) return;

    final Pair<PsiElement, Handler> pair = findHandler(element);
    System.out.println("pair = " + pair);
    if (pair == null) return;
    element = pair.first;
    final Handler handler = pair.second;
    System.out.println("handler = " + handler);

    final String newText = handler.processText(element);
    System.out.println("newText = " + newText);
    final PsiElement newElement = handler.createReplacement(element, newText);
    System.out.println("newElement = " + newElement);
    element.replace(newElement);
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

  private static abstract class Handler {
    @Nullable
    public abstract PsiElement findApplicable(final PsiElement element);

    public String processText(final PsiElement element) {
      final String text = element.getText();
      final StringBuilder sb = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        final char ch = text.charAt(i);
        if (!shouldConvert(ch)) {
          sb.append(ch);
        }
        else {
          convert(sb, ch);
        }
      }
      return sb.toString();
    }

    protected abstract void convert(StringBuilder sb, char ch);

    public abstract PsiElement createReplacement(final PsiElement element, final String newText);
  }

  private static final Handler[] ourHandlers = { new MyLiteralHandler(), new MyDocCommentHandler(), new MyCommentHandler() };

  private static class MyLiteralHandler extends Handler {
    private static final TokenSet ourLiterals = TokenSet.create(JavaTokenType.CHARACTER_LITERAL, JavaTokenType.STRING_LITERAL);

    public PsiElement findApplicable(final PsiElement element) {
      return element instanceof PsiJavaToken && ourLiterals.contains(((PsiJavaToken)element).getTokenType()) ?
             element.getParent() : null;
    }

    public PsiElement createReplacement(final PsiElement element, final String newText) {
      return JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText(newText, element.getParent());
    }

    protected void convert(final StringBuilder sb, final char ch) {
      sb.append(String.format("\\u%04x", (int)ch));
    }
  }

  private static class MyDocCommentHandler extends Handler {
    private static Map<Character, String> ourEntities = null;

    public PsiElement findApplicable(final PsiElement element) {
      if (element instanceof PsiDocComment) return element;
      if (element instanceof PsiDocToken) return element.getParent();
      if (element instanceof PsiWhiteSpace) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiDocComment) return parent;
      }
      return null;
    }

    @Override
    public String processText(final PsiElement element) {
      loadEntities(element.getProject());
      return super.processText(element);
    }

    protected void convert(final StringBuilder sb, final char ch) {
      assert ourEntities != null;
      final String entity = ourEntities.get(ch);
      if (entity != null) {
        sb.append('&').append(entity).append(';');
      }
      else {
        sb.append("&#x").append(Integer.toHexString(ch)).append(';');
      }
    }

    public PsiElement createReplacement(final PsiElement element, final String newText) {
      return JavaPsiFacade.getElementFactory(element.getProject()).createDocCommentFromText(newText, element.getParent());
    }

    private static void loadEntities(final Project project) {
      if (ourEntities != null) return;

      final XmlFile file;
      try {
        final String url = ExternalResourceManager.getInstance().getResourceLocation(XmlUtil.HTML4_LOOSE_URI, project);
        if (url == null) { LOG.error("Namespace not found: " + XmlUtil.HTML4_LOOSE_URI); return; }
        final VirtualFile vFile = VfsUtil.findFileByURL(new URL(url));
        if (vFile == null) { LOG.error("Resource not found: " + url); return; }
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (!(psiFile instanceof XmlFile)) { LOG.error("Unexpected resource: " + psiFile); return; }
        file = (XmlFile)psiFile;
      }
      catch (MalformedURLException e) {
        LOG.error(e); return;
      }

      ourEntities = new HashMap<Character, String>();
      final Pattern pattern = Pattern.compile("&#(\\d+);");
      XmlUtil.processXmlElements(file, new PsiElementProcessor() {
        public boolean execute(PsiElement element) {
          if (element instanceof XmlEntityDecl) {
            final XmlEntityDecl entity = (XmlEntityDecl)element;
            final Matcher m = pattern.matcher(entity.getValueElement().getValue());
            if (m.matches()) {
              final char i = (char)Integer.parseInt(m.group(1));
              if (shouldConvert(i)) {
                ourEntities.put(i, entity.getName());
              }
            }
          }
          return true;
        }
      }, true);
    }
  }

  private static class MyCommentHandler extends MyDocCommentHandler {
    public PsiElement findApplicable(final PsiElement element) {
      return element instanceof PsiComment ? element : null;
    }

    public PsiElement createReplacement(final PsiElement element, final String newText) {
      return JavaPsiFacade.getElementFactory(element.getProject()).createCommentFromText(newText, element.getParent());
    }
  }
}
