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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringTokenizer;

/**
 * @author yole
 */
public abstract class ElementSignatureProvider {
  public static ExtensionPointName<ElementSignatureProvider> EP_NAME = ExtensionPointName.create("com.intellij.elementSignatureProvider");

  @Nullable
  public abstract String getSignature(@NotNull PsiElement element);

  @Nullable
  public PsiElement restoreBySignature(@NotNull PsiFile file, @NotNull String signature, @Nullable StringBuilder processingInfoStorage) {
    int semicolonIndex = signature.indexOf(';');
    PsiElement parent;

    if (semicolonIndex >= 0) {
      String parentSignature = signature.substring(semicolonIndex + 1);
      if (processingInfoStorage != null) {
        processingInfoStorage.append(String.format("Restoring parent by signature '%s'...%n", parentSignature));
      } 
      parent = restoreBySignature(file, parentSignature, processingInfoStorage);
      if (processingInfoStorage != null) {
        processingInfoStorage.append(String.format("Restored parent by signature '%s': %s%n", parentSignature, parent));
      }
      if (parent == null) return null;
      signature = signature.substring(0, semicolonIndex);
    }
    else {
      parent = file;
    }

    StringTokenizer tokenizer = new StringTokenizer(signature, "#");
    String type = tokenizer.nextToken();
    if (processingInfoStorage != null) {
      processingInfoStorage.append(String.format(
        "Restoring target element by signature '%s'. Parent: %s, same as the given parent: %b%n", signature, parent, parent == file
      ));
    }
    return restoreBySignatureTokens(file, parent, type, tokenizer);
  }

  @Nullable
  protected PsiElement restoreBySignatureTokens(@NotNull PsiFile file, @NotNull PsiElement parent, String type, StringTokenizer tokenizer) {
    return null;
  }

  protected static <T extends PsiNamedElement> int getChildIndex(T element, PsiElement parent, String name, Class<T> hisClass) {
    PsiElement[] children = parent.getChildren();
    int index = 0;

    for (PsiElement child : children) {
      if (ReflectionCache.isAssignable(hisClass, child.getClass())) {
        T namedChild = hisClass.cast(child);
        final String childName = namedChild.getName();

        if (Comparing.equal(name, childName)) {
          if (namedChild.equals(element)) {
            return index;
          }
          index++;
        }
      }
    }

    return index;
  }

  @Nullable
  protected static <T extends PsiNamedElement> T restoreElementInternal(@NotNull PsiElement parent, String name, int index, Class<T> hisClass) {
    PsiElement[] children = parent.getChildren();

    for (PsiElement child : children) {
      if (ReflectionCache.isAssignable(hisClass, child.getClass())) {
        T namedChild = hisClass.cast(child);
        final String childName = namedChild.getName();

        if (Comparing.equal(name, childName)) {
          if (index == 0) {
            return namedChild;
          }
          index--;
        }
      }
    }

    return null;
  }
}
