/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
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
    for(ReadWriteAccessDetector accessDetector: Extensions.getExtensions(EP_NAME)) {
      if (accessDetector.isReadWriteAccessible(element)) {
        detector = accessDetector;
        break;
      }
    }
    return detector;
  }

  public enum Access { Read, Write, ReadWrite }

  public abstract boolean isReadWriteAccessible(@NotNull PsiElement element);
  public abstract boolean isDeclarationWriteAccess(@NotNull PsiElement element);
  @NotNull
  public abstract Access getReferenceAccess(@NotNull PsiElement referencedElement, @NotNull PsiReference reference);
  @NotNull
  public abstract Access getExpressionAccess(@NotNull PsiElement expression);
}
