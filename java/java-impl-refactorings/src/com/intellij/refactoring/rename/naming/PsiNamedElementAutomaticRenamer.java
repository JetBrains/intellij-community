/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    if (newCanonicalName.length() == 0) {
      LOG.error("oldName = " + getOldName() + ", newName = " + getNewName() + ", name = " + elementName + ", canonicalName = " +
                elementName + ", newCanonicalName = " + newCanonicalName);
    }
    return newCanonicalName;
  }
}
