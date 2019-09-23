// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.IOUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.Pair.pair;

public class ConvertToBasicLatinAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(ConvertToBasicLatinAction.class);

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
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      Pair<PsiElement, Handler> pair = findHandler(element);
      if (pair != null) {
        return !IOUtil.isAscii(pair.first.getText());
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    Pair<PsiElement, Handler> pair = findHandler(element);
    if (pair == null) return;
    PsiElement toReplace = pair.first;
    PsiElement newElement = pair.second.getSubstitution(toReplace);
    toReplace.replace(newElement);
  }

  private static class Lazy {
    private static final Handler[] ourHandlers = {new LiteralHandler(), new DocCommentHandler(), new CommentHandler()};
  }

  @Nullable
  private static Pair<PsiElement, Handler> findHandler(PsiElement element) {
    for (Handler handler : Lazy.ourHandlers) {
      PsiElement applicable = handler.findApplicable(element);
      if (applicable != null) {
        return pair(applicable, handler);
      }
    }
    return null;
  }

  private abstract static class Handler {
    abstract @Nullable PsiElement findApplicable(PsiElement element);

    PsiElement getSubstitution(PsiElement element) {
      String text = element.getText();
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (isBasicLatin(ch)) {
          sb.append(ch);
        }
        else {
          convert(sb, ch);
        }
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
      return getSubstitution(factory, element, sb.toString());
    }

    protected static boolean isBasicLatin(char ch) {
      return Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.BASIC_LATIN;
    }

    protected abstract void convert(StringBuilder sb, char ch);

    protected abstract PsiElement getSubstitution(PsiElementFactory factory, PsiElement element, String newText);
  }


  private static class LiteralHandler extends Handler {
    private static final TokenSet LITERALS = TokenSet.create(JavaTokenType.CHARACTER_LITERAL, JavaTokenType.STRING_LITERAL);

    @Override
    PsiElement findApplicable(PsiElement element) {
      if (element instanceof PsiJavaToken && LITERALS.contains(((PsiJavaToken)element).getTokenType())) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiLiteralExpression) {
          return parent;
        }
      }
      return null;
    }

    @Override
    protected PsiElement getSubstitution(PsiElementFactory factory, PsiElement element, String newText) {
      return factory.createExpressionFromText(newText, element.getParent());
    }

    @Override
    protected void convert(StringBuilder sb, char ch) {
      sb.append(String.format("\\u%04x", (int)ch));
    }
  }

  private static class DocCommentHandler extends Handler {
    private static Map<Character, String> ourEntities;

    @Override
    PsiElement findApplicable(PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, PsiDocComment.class, false);
    }

    @Override
    PsiElement getSubstitution(PsiElement element) {
      loadEntities(element.getProject());
      return ourEntities != null ? super.getSubstitution(element) : element;
    }

    @Override
    protected void convert(StringBuilder sb, char ch) {
      String entity = ourEntities.get(ch);
      if (entity != null) {
        sb.append('&').append(entity).append(';');
      }
      else {
        sb.append("&#x").append(Integer.toHexString(ch)).append(';');
      }
    }

    @Override
    protected PsiElement getSubstitution(PsiElementFactory factory, PsiElement element, String newText) {
      return factory.createCommentFromText(newText, element.getParent());
    }

    private static void loadEntities(Project project) {
      if (ourEntities != null) return;

      XmlFile file;
      try {
        String url = ExternalResourceManager.getInstance().getResourceLocation(XmlUtil.HTML4_LOOSE_URI, project);
        if (url == null) { LOG.error("Namespace not found: " + XmlUtil.HTML4_LOOSE_URI); return; }
        VirtualFile vFile = VfsUtil.findFileByURL(new URL(url));
        if (vFile == null) { LOG.error("Resource not found: " + url); return; }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (!(psiFile instanceof XmlFile)) { LOG.error("Unexpected resource: " + psiFile); return; }
        file = (XmlFile)psiFile;
      }
      catch (MalformedURLException e) {
        LOG.error(e); return;
      }

      Map<Character, String> entities = new HashMap<>();
      Pattern pattern = Pattern.compile("&#(\\d+);");
      XmlUtil.processXmlElements(file, element -> {
        if (element instanceof XmlEntityDecl) {
          XmlEntityDecl entity = (XmlEntityDecl)element;
          Matcher m = pattern.matcher(entity.getValueElement().getValue());
          if (m.matches()) {
            char i = (char)Integer.parseInt(m.group(1));
            if (!isBasicLatin(i)) {
              entities.put(i, entity.getName());
            }
          }
        }
        return true;
      }, true);

      ourEntities = entities;
    }
  }

  private static class CommentHandler extends DocCommentHandler {
    @Override
    PsiElement findApplicable(PsiElement element) {
      return element instanceof PsiComment ? element : null;
    }
  }
}