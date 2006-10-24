/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiType;

/**
 * Converter for {@link com.intellij.psi.PsiType} that uses {@link com.intellij.psi.PsiType#getCanonicalText()}
 * as string representation
 *
 * @author peter
 */
public abstract class CanonicalPsiTypeConverter extends Converter<PsiType> {
}
