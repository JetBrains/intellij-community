/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang;

/**
 * A language that isn't meant to be a file's toplevel language, and it can't be injected. Probably,
 * it's a language of some chameleon (see {@link com.intellij.psi.tree.ChameleonTrasformer})
 *
 * @see com.intellij.psi.templateLanguages.TemplateLanguage
 * @see InjectableLanguage
 * @author peter
 */
public interface DependentLanguage {
}
