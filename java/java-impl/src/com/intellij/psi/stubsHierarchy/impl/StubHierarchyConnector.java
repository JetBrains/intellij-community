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

public class StubHierarchyConnector implements HierarchyConnector {
  private final NameEnvironment myNameEnvironment;
  private final StubResolver myResolve;

  protected StubHierarchyConnector(NameEnvironment nameEnvironment, Symbols symbols) {
    this.myNameEnvironment = nameEnvironment;
    myResolve = new StubResolver(symbols);
  }

  public void connect(Symbol sym) {
    Symbol.ClassSymbol c = (Symbol.ClassSymbol) sym;

    if (c.myOwner instanceof Symbol.ClassSymbol) {
      ((Symbol.ClassSymbol)c.myOwner).connect();
    }

    // Determine supertype.
    Set<Symbol> supertypes = new HashSet<Symbol>();
    for (QualifiedName name : c.mySuperNames) {
      if (c.isCompiled()) {
        if (name != null) {
          Collections.addAll(supertypes, myResolve.findGlobalType(name));
        }
      } else {
        Set<Symbol> based = myResolve.resolveBase(c, name.myComponents);
        supertypes.addAll(based);
      }
    }

    if (c.myQualifiedName == myNameEnvironment.java_lang_Object) {
      c.mySuperClasses = Symbol.ClassSymbol.EMPTY_ARRAY;
    } else {
      for (Iterator<Symbol> iter = supertypes.iterator(); iter.hasNext();) {
        Symbol s = iter.next();
        if (!(s instanceof Symbol.ClassSymbol) || s.myQualifiedName == myNameEnvironment.java_lang_Object) {
            iter.remove();
        }
      }
      if (supertypes.isEmpty()) {
        c.mySuperClasses = Symbol.ClassSymbol.EMPTY_ARRAY;
      }
      else {
        c.mySuperClasses = supertypes.toArray(new Symbol.ClassSymbol[supertypes.size()]);
      }
    }

    // cleaning up
    c.mySuperNames = null;
    c.myUnitInfo = null;
  }

}
