// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFragment;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ResourceUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.xml.util.XmlUtil;
import com.siyeh.InspectionGadgetsBundle;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConvertToBasicLatinInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(ConvertToBasicLatinInspection.class);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      private void handle(@NotNull PsiElement element) {
        if (IOUtil.isAscii(element.getText())) return;
        holder.registerProblem(element, InspectionGadgetsBundle.message("inspection.non.basic.latin.character.display.name"), new ConvertToBasicLatinFix());
      }

      @Override
      public void visitComment(@NotNull PsiComment comment) {
        super.visitComment(comment);
        if (!(comment instanceof PsiDocComment)) {
          handle(comment);
        }
      }

      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        super.visitLiteralExpression(expression);
        if (!PsiUtil.isJavaToken(expression.getFirstChild(), ElementType.TEXT_LITERALS)) return;
        handle(expression);
      }

      @Override
      public void visitFragment(@NotNull PsiFragment fragment) {
        super.visitFragment(fragment);
        handle(fragment);
      }

      @Override
      public void visitDocComment(@NotNull PsiDocComment comment) {
        super.visitDocComment(comment);
        handle(comment);
      }
    };
  }

  private abstract static class Handler {
    @NotNull
    PsiElement buildReplacement(@NotNull Project project, @NotNull PsiElement element) {
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
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      return buildReplacement(factory, element, sb.toString());
    }

    static boolean isBasicLatin(char ch) {
      return Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.BASIC_LATIN;
    }

    protected abstract void convert(@NotNull StringBuilder sb, char ch);

    protected abstract @NotNull PsiElement buildReplacement(@NotNull PsiElementFactory factory, @NotNull PsiElement element, @NotNull String newText);
  }

  private static class LiteralHandler extends Handler {
    @Override
    protected @NotNull PsiElement buildReplacement(@NotNull PsiElementFactory factory,
                                                   @NotNull PsiElement element,
                                                   @NotNull String newText) {
      return factory.createExpressionFromText(newText, element.getParent());
    }

    @Override
    protected void convert(@NotNull StringBuilder sb, char ch) {
      sb.append(String.format("\\u%04X", (int)ch));
    }
  }

  private static class FragmentHandler extends LiteralHandler {
    @Override
    protected @NotNull PsiElement buildReplacement(@NotNull PsiElementFactory factory,
                                                   @NotNull PsiElement element,
                                                   @NotNull String newText) {
      return factory.createStringTemplateFragment(newText, ((PsiFragment)element).getTokenType(), element);
    }
  }

  private static class DocCommentHandler extends Handler {
    /**
     * The HTML 4 character entity sets, bundled with {@code intellij.xml.psi.impl}. They are plain
     * {@code <!ENTITY name "&#NNN;">} declaration lists without external entity references, so they can be parsed
     * standalone - unlike {@code xhtml1-transitional.dtd}, which only pulls them in as external parameter entities and
     * therefore needs the whole XML resolve stack plus VFS access to the containing jar.
     */
    private static final String[] ENTITY_SETS = {"xhtml-lat1.ent", "xhtml-symbol.ent", "xhtml-special.ent"};

    private static Int2ObjectMap<String> ourEntities;

    @Override
    @NotNull
    PsiElement buildReplacement(@NotNull Project project, @NotNull PsiElement element) {
      loadEntities(project);
      return super.buildReplacement(project, element);
    }

    @Override
    protected void convert(@NotNull StringBuilder sb, char ch) {
      String entity = ourEntities.get(ch);
      if (entity != null) {
        sb.append('&').append(entity).append(';');
      }
      else {
        sb.append("&#x").append(Integer.toHexString(ch).toUpperCase(Locale.ENGLISH)).append(';');
      }
    }

    @Override
    protected @NotNull PsiElement buildReplacement(@NotNull PsiElementFactory factory,
                                                   @NotNull PsiElement element,
                                                   @NotNull String newText) {
      return factory.createCommentFromText(newText, element.getParent());
    }

    /**
     * Collects the {@code character -> entity name} mapping. Never leaves {@link #ourEntities} unset: when the entity
     * sets cannot be read or DTD support is not available (e.g. in the language server, which does not load the XML
     * language), the map stays empty and {@link #convert} falls back to numeric character references.
     */
    private static void loadEntities(@NotNull Project project) {
      if (ourEntities != null) return;

      Int2ObjectMap<String> entities = new Int2ObjectOpenHashMap<>();
      Pattern pattern = Pattern.compile("&#(\\d+);");
      PsiFileFactory factory = PsiFileFactory.getInstance(project);
      for (String name : ENTITY_SETS) {
        String text = loadEntitySet(name);
        if (text == null) continue;
        PsiFile psiFile = factory.createFileFromText(name, DTDFileType.INSTANCE, text);
        if (!(psiFile instanceof XmlFile file)) {
          LOG.warn("DTD support is not available, falling back to numeric character references: " + psiFile);
          break;
        }
        XmlUtil.processXmlElements(file, element -> {
          if (element instanceof XmlEntityDecl entity) {
            XmlAttributeValue value = entity.getValueElement();
            if (value == null) return true;
            Matcher m = pattern.matcher(value.getValue());
            if (m.matches()) {
              char i = (char)Integer.parseInt(m.group(1));
              if (!isBasicLatin(i)) {
                entities.put(i, entity.getName());
              }
            }
          }
          return true;
        }, true);
      }

      ourEntities = entities;
    }

    private static @Nullable String loadEntitySet(@NotNull String name) {
      String path = StringUtil.trimStart(ExternalResourceManagerEx.STANDARD_SCHEMAS, "/") + name;
      byte[] bytes = ResourceUtil.getResourceAsBytesSafely(path, ExternalResourceManagerEx.class.getClassLoader());
      if (bytes == null) {
        LOG.warn("Resource not found: " + path);
        return null;
      }
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }

  private static class CommentHandler extends DocCommentHandler {
  }

  private static class ConvertToBasicLatinFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.non.basic.latin.character.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final Handler handler;
      if (element instanceof PsiLiteralExpression) {
        handler = new LiteralHandler();
      }
      else if (element instanceof PsiDocComment) {
        handler = new DocCommentHandler();
      }
      else if (element instanceof PsiComment) {
        handler = new CommentHandler();
      }
      else if (element instanceof PsiFragment) {
        handler = new FragmentHandler();
      }
      else {
        return;
      }
      element.replace(handler.buildReplacement(project, element));
    }
  }
}