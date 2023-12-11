// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

@SuppressWarnings({"HardCodedStringLiteral"})
public final class JavaElementSignatureProvider extends AbstractElementSignatureProvider {
  private static final Logger LOG = Logger.getInstance(JavaElementSignatureProvider.class);

  @Override
  @Nullable
  public String getSignature(@NotNull final PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (!(file instanceof PsiJavaFile)) {
      return null;
    }
    if (element instanceof PsiImportList) {
      if (element.equals(((PsiJavaFile)file).getImportList())) {
        return "imports";
      }
      else {
        return null;
      }
    }
    if (element instanceof PsiMethod method) {
      PsiElement parent = method.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("method").append(ELEMENT_TOKENS_SEPARATOR);
      String name = method.getName();
      buffer.append(name);
      buffer.append(ELEMENT_TOKENS_SEPARATOR);
      int childIndex = getChildIndex(method, parent, name, PsiMethod.class);
      if (childIndex < 0) return null;
      buffer.append(childIndex);

      if (parent instanceof PsiClass) {
        String parentSignature = getSignature(parent);
        if (parentSignature == null) return null;
        buffer.append(";");
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    if (element instanceof PsiClass aClass) {
      PsiElement parent = aClass.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("class").append(ELEMENT_TOKENS_SEPARATOR);
      if (parent instanceof PsiClass || parent instanceof PsiFile) {
        String name = aClass.getName();
        buffer.append(name);
        buffer.append(ELEMENT_TOKENS_SEPARATOR);
        int childIndex = getChildIndex(aClass, parent, name, PsiClass.class);
        if (childIndex < 0) return null;
        buffer.append(childIndex);

        if (parent instanceof PsiClass) {
          String parentSignature = getSignature(parent);
          if (parentSignature == null) return null;
          buffer.append(ELEMENTS_SEPARATOR);
          buffer.append(parentSignature);
        }
      }
      else {
        buffer.append(aClass.getTextRange().getStartOffset());
        buffer.append(":");
        buffer.append(aClass.getTextRange().getEndOffset());
      }

      return buffer.toString();
    }
    if (element instanceof PsiClassInitializer initializer) {
      PsiElement parent = initializer.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("initializer").append(ELEMENT_TOKENS_SEPARATOR);

      int index = 0;
      PsiElement[] children = parent.getChildren();
      for (PsiElement child : children) {
        if (child instanceof PsiClassInitializer) {
          if (child.equals(initializer)) break;
          index++;
        }
      }
      buffer.append(ELEMENT_TOKENS_SEPARATOR);
      buffer.append(index);

      if (parent instanceof PsiClass) {
        String parentSignature = getSignature(parent);
        if (parentSignature == null) return null;
        buffer.append(ELEMENTS_SEPARATOR);
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    if (element instanceof PsiField field) { // needed for doc-comments only
      PsiElement parent = field.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("field").append(ELEMENT_TOKENS_SEPARATOR);
      String name = field.getName();
      buffer.append(name);

      buffer.append(ELEMENT_TOKENS_SEPARATOR);
      int childIndex = getChildIndex(field, parent, name, PsiField.class);
      if (childIndex < 0) return null;
      buffer.append(childIndex);

      if (parent instanceof PsiClass) {
        String parentSignature = getSignature(parent);
        if (parentSignature == null) return null;
        buffer.append(ELEMENTS_SEPARATOR);
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    if (element instanceof PsiDocComment) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("docComment").append(ELEMENTS_SEPARATOR);

      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass) && !(parent instanceof PsiMethod) && !(parent instanceof PsiField)) {
        return null;
      }
      if (!element.equals(((PsiDocCommentOwner)parent).getDocComment())) {
        return null;
      }
      String parentSignature = getSignature(parent);
      if (parentSignature == null) return null;
      buffer.append(parentSignature);

      return buffer.toString();
    }
    return null;
  }

  @Override
  protected PsiElement restoreBySignatureTokens(@NotNull PsiFile file,
                                                @NotNull PsiElement parent,
                                                @NotNull String type,
                                                @NotNull StringTokenizer tokenizer,
                                                @Nullable StringBuilder processingInfoStorage) {
    return switch (type) {
      case "imports" -> file instanceof PsiJavaFile javaFile ? javaFile.getImportList() : null;
      case "method" -> {
        String name = tokenizer.nextToken();
        try {
          int index = Integer.parseInt(tokenizer.nextToken());
          yield restoreElementInternal(parent, name, index, PsiMethod.class);
        }
        catch (NumberFormatException e) {
          LOG.error(e);
          yield null;
        }
      }
      case "class" -> {
        String name = tokenizer.nextToken();

        PsiNameHelper nameHelper = PsiNameHelper.getInstance(file.getProject());
        if (nameHelper.isIdentifier(name)) {
          int index = 0;
          try {
            index = Integer.parseInt(tokenizer.nextToken());
          }
          catch (NoSuchElementException e) { //To read previous XML versions correctly
          }

          yield restoreElementInternal(parent, name, index, PsiClass.class);
        }
        StringTokenizer tok1 = new StringTokenizer(name, ":");
        int start = Integer.parseInt(tok1.nextToken());
        int end = Integer.parseInt(tok1.nextToken());
        PsiElement element = file.findElementAt(start);
        if (element != null) {
          TextRange range = element.getTextRange();
          while (range != null && range.getEndOffset() < end) {
            element = element.getParent();
            range = element.getTextRange();
          }

          if (range != null && range.getEndOffset() == end && element instanceof PsiClass) {
            yield element;
          }
        }

        yield null;
      }
      case "initializer" -> {
        try {
          int index = Integer.parseInt(tokenizer.nextToken());

          PsiElement[] children = parent.getChildren();
          for (PsiElement child : children) {
            if (child instanceof PsiClassInitializer) {
              if (index == 0) {
                yield child;
              }
              index--;
            }
          }

          yield null;
        }
        catch (NumberFormatException e) {
          LOG.error(e);
          yield null;
        }
      }
      case "field" -> {
        String name = tokenizer.nextToken();

        try {
          int index = 0;
          try {
            index = Integer.parseInt(tokenizer.nextToken());
          }
          catch (NoSuchElementException e) { //To read previous XML versions correctly
          }

          yield restoreElementInternal(parent, name, index, PsiField.class);
        }
        catch (NumberFormatException e) {
          LOG.error(e);
          yield null;
        }
      }
      case "docComment" -> {
        if (parent instanceof PsiClass psiClass) {
          yield psiClass.getDocComment();
        }
        else if (parent instanceof PsiMethod psiMethod) {
          yield psiMethod.getDocComment();
        }
        else if (parent instanceof PsiField psiField) {
          yield psiField.getDocComment();
        }
        else {
          yield null;
        }
      }
      default -> null;
    };
  }
}
