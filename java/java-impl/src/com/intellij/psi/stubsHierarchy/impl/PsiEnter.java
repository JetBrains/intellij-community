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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.util.indexing.FileBasedIndex;

import java.util.ArrayList;
import java.util.Arrays;

import static com.intellij.psi.stubsHierarchy.impl.Symbol.*;

public class PsiEnter {
  private final Symbols mySymbols;
  private final NameEnvironment myNameEnvironment;
  private final PsiHierachyConnector myPsiHierachyConnector;
  private ArrayList<ClassSymbol> myQueue = new ArrayList<ClassSymbol>();


  public PsiEnter(NameEnvironment nameEnvironment, Symbols symbols) {
    this.myNameEnvironment = nameEnvironment;
    this.mySymbols = symbols;
    myPsiHierachyConnector = new PsiHierachyConnector(nameEnvironment, symbols);
  }

  void enter(final PsiClassOwner psiClassOwner) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        enterTopLevels(psiClassOwner);
      }
    });
  }

  private void enterTopLevels(PsiClassOwner owner) {
    String pkgName = owner.getPackageName();
    PackageSymbol pkg = StringUtil.isEmpty(pkgName) ?
                        mySymbols.myRootPackage : mySymbols.enterPackage(myNameEnvironment.fromString(pkgName, true));
    for (PsiClass psiClass : owner.getClasses()) {
      enter(psiClass, pkg);
    }
  }

  private ClassSymbol enter(PsiClass psiClass, Symbol owner) {
    if (HierarchyService.IGNORE_LOCAL_CLASSES && psiClass.getName() == null) {
      return null;
    }

    int psiFlags = translateFlags(psiClass);
    int fileId = FileBasedIndex.getFileId(psiClass.getContainingFile().getVirtualFile());
    ClassAnchor.DirectClassAnchor classAnchor = new ClassAnchor.DirectClassAnchor(fileId, psiClass);
    int shortName = myNameEnvironment.simpleName(psiClass.getName(), true);
    ClassSymbol classSymbol = mySymbols.enterClass(classAnchor, psiFlags, shortName, owner, null, null, myPsiHierachyConnector);

    if (myQueue != null) {
      myQueue.add(classSymbol);
    }

    ClassSymbol[] members = enter(psiClass.getInnerClasses(), classSymbol);
    classSymbol.setMembers(members);
    return classSymbol;
  }

  private ClassSymbol[] enter(PsiClass[] psiClasses, Symbol owner) {
    ClassSymbol[] members = new ClassSymbol[psiClasses.length];
    int i = 0;
    for (PsiClass psiClass : psiClasses) {
      ClassSymbol member = enter(psiClass, owner);
      if (member != null && member.myShortName != 0) {
        members[i++] = member;
      }
    }
    members = members.length == 0 ? ClassSymbol.EMPTY_ARRAY : Arrays.copyOf(members, i);
    Arrays.sort(members, CLASS_SYMBOL_BY_NAME_COMPARATOR);
    return members;
  }

  public static int translateFlags(PsiClass psiClass) {
    int flags = 0;
    if (psiClass.isInterface()) {
      flags |= IndexTree.INTERFACE;
    }
    if (psiClass.isEnum()) {
      flags |= IndexTree.ENUM;
    }
    if (psiClass.isAnnotationType()) {
      flags |= IndexTree.ANNOTATION;
    }
    if (psiClass.hasModifierProperty(PsiModifier.STATIC)) {
      flags |= IndexTree.STATIC;
    }
    return flags;
  }

  public void connect() {
    for (ClassSymbol classSymbol : myQueue) {
      classSymbol.connect();
    }
    myQueue = null;
  }
}
