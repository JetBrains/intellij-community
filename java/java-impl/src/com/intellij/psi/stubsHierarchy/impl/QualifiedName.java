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
import java.util.Set;

abstract class QualifiedName {

  abstract void resolveCandidates(StubResolver resolver, Symbol.ClassSymbol place, Set<Symbol.ClassSymbol> result)
    throws IncompleteHierarchyException;

  static class OfComponents extends QualifiedName {
    @ShortName final int[] components;

    OfComponents(int[] components) {
      this.components = components;
    }

    @Override
    void resolveCandidates(StubResolver resolver, Symbol.ClassSymbol place, Set<Symbol.ClassSymbol> result)
      throws IncompleteHierarchyException {
      for (Symbol symbol : resolver.resolveBase(place, components)) {
        if (symbol instanceof Symbol.ClassSymbol) {
          result.add((Symbol.ClassSymbol)symbol);
        }
      }
    }
  }

  static class OfSingleComponent extends QualifiedName {
    @ShortName final int shortName;

    OfSingleComponent(@ShortName int shortName) {
      this.shortName = shortName;
    }

    @Override
    void resolveCandidates(StubResolver resolver, Symbol.ClassSymbol place, Set<Symbol.ClassSymbol> result)
      throws IncompleteHierarchyException {
      for (Symbol symbol : resolver.resolveUnqualified(place, shortName, false)) {
        if (symbol instanceof Symbol.ClassSymbol) {
          result.add((Symbol.ClassSymbol)symbol);
        }
      }
    }
  }

  static class Interned extends QualifiedName {
    @QNameHash final int id;

    Interned(int id) {
      this.id = id;
    }

    @Override
    void resolveCandidates(StubResolver resolver, Symbol.ClassSymbol place, Set<Symbol.ClassSymbol> result)
      throws IncompleteHierarchyException {
      Symbol.ClassSymbol[] candidates = resolver.findGlobalType(id);
      if (candidates.length == 0) {
        throw IncompleteHierarchyException.INSTANCE;
      }

      Collections.addAll(result, candidates);
    }
  }
}
