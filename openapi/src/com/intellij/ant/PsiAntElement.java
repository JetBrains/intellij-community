/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ant;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.SearchScope;

/**
 * @author dyoma
 */
public interface PsiAntElement extends PsiNamedElement {
  PsiAntElement copy();
  SearchScope getSearchScope();
  AntElementRole getRole();
}
