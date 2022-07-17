// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;

/**
 * The element specifying what exactly is being documented by this tag.
 *
 * (for example, the parameter name for a param tag or the exception name for a throws tag).
 */
public interface PsiDocTagValue extends PsiElement {

}
