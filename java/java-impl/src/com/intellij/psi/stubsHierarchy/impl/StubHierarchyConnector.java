/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class StubHierarchyConnector {
  private final StubResolver myResolve;

  protected StubHierarchyConnector(Symbols symbols) {
    myResolve = new StubResolver(symbols, this);
  }

  void connect(Symbol sym) {
    Symbol.ClassSymbol c = (Symbol.ClassSymbol) sym;

    if (c.myOwner instanceof Symbol.ClassSymbol) {
      ((Symbol.ClassSymbol)c.myOwner).connect(this);
    }

    Object supers = c.mySuperClasses;
    if (supers == null) {
      c.setSupers(Collections.emptySet());
      return;
    }

    // Determine supertype.
    Set<Symbol.ClassSymbol> supertypes = new HashSet<>();

    try {
      if (supers instanceof QualifiedName[]) {
        for (QualifiedName name : (QualifiedName[])supers) {
          name.resolveCandidates(myResolve, c, supertypes);
        }
      } else {
        ((QualifiedName)supers).resolveCandidates(myResolve, c, supertypes);
      }
    }
    catch (IncompleteHierarchyException ignore) {
      c.markHierarchyIncomplete();
      return;
    }

    for (Iterator<Symbol.ClassSymbol> iterator = supertypes.iterator(); iterator.hasNext();) {
      if (isJavaLangObject(iterator.next())) {
        iterator.remove();
      }
    }
    c.setSupers(supertypes);
  }

  private static boolean isJavaLangObject(Symbol s) {
    return s.myShortName == NameEnvironment.OBJECT_NAME &&
           s.myOwner instanceof Symbol.PackageSymbol &&
           ((Symbol.PackageSymbol)s.myOwner).myQualifiedName == NameEnvironment.java_lang;
  }
}
