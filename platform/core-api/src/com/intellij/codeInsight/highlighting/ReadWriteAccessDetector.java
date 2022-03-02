// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension is used in Find Usages, Highlighting and other places to
 * classify the psi expression as:
 * <ul>
 * <li>read variable expression (e.g. {@code int var = expression;} ), see {@link Access#Read}</li>
 * <li>write variable expression (e.g. {@code expression = value;} ), see {@link Access#Write}</li> or
 * <li>read/write variable expression (e.g. {@code var++;} ), see {@link Access#ReadWrite}</li>
 * </ul>
 *
 */
public abstract class ReadWriteAccessDetector {
  public static final ExtensionPointName<ReadWriteAccessDetector> EP_NAME = ExtensionPointName.create("com.intellij.readWriteAccessDetector");

  @Nullable
  public static ReadWriteAccessDetector findDetector(@NotNull PsiElement element) {
    ReadWriteAccessDetector detector = null;
    for(ReadWriteAccessDetector accessDetector: EP_NAME.getExtensionList()) {
      if (accessDetector.isReadWriteAccessible(element)) {
        detector = accessDetector;
        break;
      }
    }
    return detector;
  }

  public enum Access {
    Read, Write, ReadWrite;
    public boolean isReferencedForRead() {
      return this == Read || this == ReadWrite;
    }
    public boolean isReferencedForWrite() {
      return this == Write || this == ReadWrite;
    }
  }

  public abstract boolean isReadWriteAccessible(@NotNull PsiElement element);
  public abstract boolean isDeclarationWriteAccess(@NotNull PsiElement element);
  @NotNull
  public abstract Access getReferenceAccess(@NotNull PsiElement referencedElement, @NotNull PsiReference reference);
  @NotNull
  public abstract Access getExpressionAccess(@NotNull PsiElement expression);
}
