/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang;

/**
 * A language that isn't meant to be a file's toplevel language, probably, its main purpose is to be injected
 * (see {@link com.intellij.psi.PsiLanguageInjectionHost}, {@link com.intellij.psi.LanguageInjector})
 *
 * @author peter
 */
public interface DependentLanguage {
}
