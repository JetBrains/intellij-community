/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang;

/**
 * A language whose main purpose is to be injected
 * (see {@link com.intellij.psi.PsiLanguageInjectionHost}, {@link com.intellij.psi.LanguageInjector})
 *
 * @see com.intellij.psi.templateLanguages.TemplateLanguage
 * @see com.intellij.lang.DependentLanguage
 * @author peter
 */
public interface InjectableLanguage {
}