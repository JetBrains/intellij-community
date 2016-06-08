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
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.stubs.Unit;
import com.intellij.reference.SoftReference;

public class HierarchyService {
  public static final boolean IGNORE_LOCAL_CLASSES = true;

  private NameEnvironment myNameEnvironment;
  private SoftReference<NameEnvironment> myNamesCache = new SoftReference<NameEnvironment>(null);

  private SingleClassHierarchy mySingleClassHierarchy;
  private Symbols mySymbols;
  private StubEnter myStubEnter;

  public static HierarchyService instance(Project project) {
    return ServiceManager.getService(project, HierarchyService.class);
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

  public void connect1() {
    myStubEnter.connect1();
  }

  public void complete2() {
    myStubEnter.connect2();
    myStubEnter = null;
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
    mySingleClassHierarchy = null;
  }

}
