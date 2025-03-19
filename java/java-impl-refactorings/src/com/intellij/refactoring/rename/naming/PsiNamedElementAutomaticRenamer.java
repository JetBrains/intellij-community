// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiNamedElement;

import java.util.List;

public abstract class PsiNamedElementAutomaticRenamer<T extends PsiNamedElement> extends AutomaticUsageRenamer<T> {
  private static final Logger LOG = Logger.getInstance(PsiNamedElementAutomaticRenamer.class);

  protected PsiNamedElementAutomaticRenamer(List<? extends T> elements, String oldName, String newName) {
    super(elements, oldName, newName);
  }

  @Override
  protected String getName(T element) {
    return element.getName();
  }

  @Override
  protected String suggestName(T element) {
    String elementName = getName(element);
    final NameSuggester suggester = new NameSuggester(getOldName(), getNewName());
    final String newCanonicalName = suggester.suggestName(elementName);
    if (newCanonicalName.isEmpty()) {
      LOG.error("oldName = " + getOldName() + ", newName = " + getNewName() + ", name = " + elementName + ", canonicalName = " +
                elementName + ", newCanonicalName = " + newCanonicalName);
    }
    return newCanonicalName;
  }
}
