/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
public class JavaElementSignatureProvider extends AbstractElementSignatureProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.JavaElementSignatureProvider");

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
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiElement parent = method.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("method").append(ELEMENT_TOKENS_SEPARATOR);
      String name = method.getName();
      buffer.append(name);
      buffer.append(ELEMENT_TOKENS_SEPARATOR);
      buffer.append(getChildIndex(method, parent, name, PsiMethod.class));

      if (parent instanceof PsiClass) {
        String parentSignature = getSignature(parent);
        if (parentSignature == null) return null;
        buffer.append(";");
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      PsiElement parent = aClass.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("class").append(ELEMENT_TOKENS_SEPARATOR);
      if (parent instanceof PsiClass || parent instanceof PsiFile) {
        String name = aClass.getName();
        buffer.append(name);
        buffer.append(ELEMENT_TOKENS_SEPARATOR);
        buffer.append(getChildIndex(aClass, parent, name, PsiClass.class));

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
    if (element instanceof PsiClassInitializer) {
      PsiClassInitializer initializer = (PsiClassInitializer)element;
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
    if (element instanceof PsiField) { // needed for doc-comments only
      PsiField field = (PsiField)element;
      PsiElement parent = field.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("field").append(ELEMENT_TOKENS_SEPARATOR);
      String name = field.getName();
      buffer.append(name);

      buffer.append(ELEMENT_TOKENS_SEPARATOR);
      buffer.append(getChildIndex(field, parent, name, PsiField.class));

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
                                                @Nullable StringBuilder processingInfoStorage)
  {
    if (type.equals("imports")) {
      if (!(file instanceof PsiJavaFile)) return null;
      return ((PsiJavaFile)file).getImportList();
    }
    else if (type.equals("method")) {
      String name = tokenizer.nextToken();
      try {
        int index = Integer.parseInt(tokenizer.nextToken());
        return restoreElementInternal(parent, name, index, PsiMethod.class);
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }
    }
    else if (type.equals("class")) {
      String name = tokenizer.nextToken();

      PsiNameHelper nameHelper = PsiNameHelper.getInstance(file.getProject());
      if (nameHelper.isIdentifier(name)) {
        int index = 0;
        try {
          index = Integer.parseInt(tokenizer.nextToken());
        }
        catch (NoSuchElementException e) { //To read previous XML versions correctly
        }

        return restoreElementInternal(parent, name, index, PsiClass.class);
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
          return element;
        }
      }

      return null;
    }
    else if (type.equals("initializer")) {
      try {
        int index = Integer.parseInt(tokenizer.nextToken());

        PsiElement[] children = parent.getChildren();
        for (PsiElement child : children) {
          if (child instanceof PsiClassInitializer) {
            if (index == 0) {
              return child;
            }
            index--;
          }
        }

        return null;
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }
    }
    else if (type.equals("field")) {
      String name = tokenizer.nextToken();

      try {
        int index = 0;
        try {
          index = Integer.parseInt(tokenizer.nextToken());
        }
        catch (NoSuchElementException e) { //To read previous XML versions correctly
        }

        return restoreElementInternal(parent, name, index, PsiField.class);
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }

    }
    else if (type.equals("docComment")) {
      if (parent instanceof PsiClass) {
        return ((PsiClass)parent).getDocComment();
      }
      else if (parent instanceof PsiMethod) {
        return ((PsiMethod)parent).getDocComment();
      }
      else if (parent instanceof PsiField) {
        return ((PsiField)parent).getDocComment();
      }
      else {
        return null;
      }
    }
    else {
      return null;
    }
  }
}
