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
  public abstract String getSignature(PsiElement element);

  @Nullable
  public PsiElement restoreBySignature(PsiFile file, String signature) {
    int semicolonIndex = signature.indexOf(';');
    PsiElement parent;

    if (semicolonIndex >= 0) {
      String parentSignature = signature.substring(semicolonIndex + 1);
      parent = restoreBySignature(file, parentSignature);
      if (parent == null) return null;
      signature = signature.substring(0, semicolonIndex);
    }
    else {
      parent = file;
    }

    StringTokenizer tokenizer = new StringTokenizer(signature, "#");
    String type = tokenizer.nextToken();
    return restoreBySignatureTokens(file, parent, type, tokenizer);
  }

  @Nullable
  protected PsiElement restoreBySignatureTokens(PsiFile file, PsiElement parent, String type, StringTokenizer tokenizer) {
    return null;
  }

  protected static <T extends PsiNamedElement> int getChildIndex(T element, PsiElement parent, String name, Class<T> hisClass) {
    PsiElement[] children = parent.getChildren();
    int index = 0;

    for (PsiElement child : children) {
      if (ReflectionCache.isAssignable(hisClass, child.getClass())) {
        T namedChild = (T)child;
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
        T namedChild = (T)child;
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
