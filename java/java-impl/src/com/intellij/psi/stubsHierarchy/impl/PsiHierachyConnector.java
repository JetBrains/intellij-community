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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;

import java.util.ArrayList;

public class PsiHierachyConnector implements HierarchyConnector {

  private final NameEnvironment myNameEnvironment;
  private final Symbols mySymbols;

  protected PsiHierachyConnector(NameEnvironment nameEnvironment, Symbols symbols) {
    this.myNameEnvironment = nameEnvironment;
    this.mySymbols = symbols;
  }

  @Override
  public void connect(final Symbol sym) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        connectInternal((Symbol.ClassSymbol)sym);
      }
    });
  }

  private void connectInternal(Symbol.ClassSymbol sym) {
    if (sym.myOwner instanceof Symbol.ClassSymbol) {
      ((Symbol.ClassSymbol)sym.myOwner).connect();
    }
    sym.mySuperClasses = getSuperTypes(sym.myClassAnchor);
  }

  private Symbol.ClassSymbol[] asSymbol(PsiClass psiClass) {
    if (psiClass == null) {
      return Symbol.ClassSymbol.EMPTY_ARRAY;
    }
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName != null) {
      QualifiedName n = myNameEnvironment.fromString(qualifiedName, false);
      if (n != null) {
        return mySymbols.loadClass(n);
      }
    }
    return Symbol.ClassSymbol.EMPTY_ARRAY;
  }

  Symbol.ClassSymbol[] getSuperTypes(SmartClassAnchor anchor) {
    PsiClass psiClass = ((SmartClassAnchor.DirectSmartClassAnchor)anchor).myPsiClass;
    PsiClass[] psiInterfaces = psiClass.getInterfaces();
    ArrayList<Symbol.ClassSymbol> superList = new ArrayList<Symbol.ClassSymbol>();
    for (PsiClass psiInterface : psiInterfaces) {
      for (Symbol.ClassSymbol cs : asSymbol(psiInterface)) {
        if (cs.myQualifiedName != myNameEnvironment.java_lang_Object) {
          superList.add(cs);
        }
      }
    }

    PsiClass psiSuperClass = psiClass.getSuperClass();
    for (Symbol.ClassSymbol cs : asSymbol(psiSuperClass)) {
      if (cs.myQualifiedName != myNameEnvironment.java_lang_Object) {
        superList.add(cs);
      }
    }

    if (superList.isEmpty()) {
      return Symbol.ClassSymbol.EMPTY_ARRAY;
    }
    return superList.isEmpty() ? Symbol.ClassSymbol.EMPTY_ARRAY : superList.toArray(new Symbol.ClassSymbol[superList.size()]);
  }
}
