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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.stubs.Unit;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;

public class HierarchyService {

  public static boolean PROCESS_PSI = true;
  public static boolean IGNORE_LOCAL_CLASSES = true;
  public static final String HIERARCHY_SERVICE = "java.hierarchy.service";

  private NameEnvironment myNameEnvironment;
  private SoftReference<NameEnvironment> myNamesCache = new SoftReference<NameEnvironment>(null);

  private SingleClassHierarchy mySingleClassHierarchy;
  private Symbols mySymbols;
  private StubEnter myStubEnter;
  private PsiEnter myPsiEnter;

  public static HierarchyService instance(Project project) {
    return ServiceManager.getService(project, HierarchyService.class);
  }

  public static boolean isEnabled() {
    return Registry.is(HIERARCHY_SERVICE);
  }

  public HierarchyService() {
    clear();
  }

  public void processUnit(IndexTree.Unit unit) {
    Unit compUnit = Translator.translate(myNameEnvironment, unit);
    if (compUnit != null) {
      myStubEnter.unitEnter(compUnit);
    }
  }

  public void processPsiClassOwner(@NotNull PsiClassOwner psiClassOwner) {
    myPsiEnter.enter(psiClassOwner);
  }

  public void connect1() {
    myStubEnter.connect1();
  }

  public void complete2() {
    myStubEnter.connect2();
    myPsiEnter.connect();

    myStubEnter = null;
    myPsiEnter = null;
  }

  public void connectSubtypes() {
    this.mySingleClassHierarchy = mySymbols.createHierarchy();
    mySymbols = null;
  }

  public SingleClassHierarchy getSingleClassHierarchy() {
    return mySingleClassHierarchy;
  }

  public void clear() {
    myNameEnvironment = myNamesCache.get();
    if (myNameEnvironment == null) {
      myNameEnvironment = new NameEnvironment();
      myNamesCache = new SoftReference<NameEnvironment>(myNameEnvironment);
    }
    mySymbols = new Symbols(myNameEnvironment);
    myStubEnter = new StubEnter(myNameEnvironment, mySymbols);
    myPsiEnter = new PsiEnter(myNameEnvironment, mySymbols);
    mySingleClassHierarchy = null;
  }

}
