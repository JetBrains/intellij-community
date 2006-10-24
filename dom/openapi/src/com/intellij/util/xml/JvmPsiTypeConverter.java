/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiType;

/**
 * Converter for {@link com.intellij.psi.PsiType} that uses JVM internal representation. See {@link Class#getName()}  
 *
 * @author peter
 */
public abstract class JvmPsiTypeConverter extends Converter<PsiType> {
}
