// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

/**
 * Represents pattern which is used in {@code instanceof} expressions or switch case labels.
 */
public interface PsiPattern extends PsiCaseLabelElement {
  PsiPattern[] EMPTY = new PsiPattern[]{};
}
