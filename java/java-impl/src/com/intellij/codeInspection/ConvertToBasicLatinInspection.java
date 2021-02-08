// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.io.IOUtil;
import com.intellij.xml.util.XmlUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConvertToBasicLatinInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final TokenSet LITERALS = TokenSet.create(JavaTokenType.CHARACTER_LITERAL, JavaTokenType.STRING_LITERAL);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Nullable
      @SuppressWarnings("DialogTitleCapitalization" /* "Basic Latin" is a proper noun */)
      private ProblemDescriptor getProblem(PsiElement element) {
        if (IOUtil.isAscii(element.getText())) return null;
        return holder.getManager().createProblemDescriptor(element,
                                                           (TextRange)null,
                                                           JavaBundle.message("inspection.convert.to.basic.latin"),
                                                           ProblemHighlightType.INFORMATION,
                                                           isOnTheFly,
                                                           new MyLocalQuickFix());
      }

      @Override
      public void visitComment(@NotNull PsiComment comment) {
        super.visitComment(comment);
        final ProblemDescriptor descriptor = getProblem(comment);
        if (descriptor != null) {
          holder.registerProblem(descriptor);
        }
      }

      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        super.visitLiteralExpression(expression);
        if (!(expression instanceof PsiLiteralExpressionImpl)) return;
        if (!LITERALS.contains(((PsiLiteralExpressionImpl)expression).getLiteralElementType())) {
          return;
        }
        final ProblemDescriptor descriptor = getProblem(expression);
        if (descriptor != null) {
          holder.registerProblem(descriptor);
        }

      }

      @Override
      public void visitDocComment(PsiDocComment comment) {
        super.visitDocComment(comment);
        final ProblemDescriptor descriptor = getProblem(comment);
        if (descriptor != null) {
          holder.registerProblem(descriptor);
        }
      }
    };
  }

  private abstract static class Handler {
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
    @Override
    protected PsiElement getSubstitution(PsiElementFactory factory, PsiElement element, String newText) {
      return factory.createExpressionFromText(newText, element.getParent());
    }

    @Override
    protected void convert(StringBuilder sb, char ch) {
      sb.append(String.format("\\u%04X", (int)ch));
    }
  }

  private static class DocCommentHandler extends Handler {
    private static Int2ObjectMap<String> ourEntities;

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
        sb.append("&#x").append(Integer.toHexString(ch).toUpperCase(Locale.ENGLISH)).append(';');
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
        if (url == null) {
          Logger.getInstance(ConvertToBasicLatinInspection.class).error("Namespace not found: " + XmlUtil.HTML4_LOOSE_URI);
          return;
        }
        VirtualFile vFile = VfsUtil.findFileByURL(new URL(url));
        if (vFile == null) {
          Logger.getInstance(ConvertToBasicLatinInspection.class).error("Resource not found: " + url);
          return;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (!(psiFile instanceof XmlFile)) {
          Logger.getInstance(ConvertToBasicLatinInspection.class).error("Unexpected resource: " + psiFile);
          return;
        }
        file = (XmlFile)psiFile;
      }
      catch (MalformedURLException e) {
        Logger.getInstance(ConvertToBasicLatinInspection.class).error(e);
        return;
      }

      Int2ObjectMap<String> entities = new Int2ObjectOpenHashMap<>();
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

  private static class CommentHandler extends DocCommentHandler {}

  private static class MyLocalQuickFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.convert.to.basic.latin");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final Handler handler;
      if (element instanceof PsiLiteralExpression) {
        handler = new LiteralHandler();
      } else if (element instanceof PsiDocComment) {
        handler = new DocCommentHandler();
      } else if (element instanceof PsiComment) {
        handler = new CommentHandler();
      } else {
        handler = null;
      }
      if (handler == null) return;
      final PsiElement newElement = handler.getSubstitution(element);
      element.replace(newElement);
    }
  }
}