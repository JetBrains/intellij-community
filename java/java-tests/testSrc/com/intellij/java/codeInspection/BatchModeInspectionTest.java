/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.List;

public class BatchModeInspectionTest extends LightCodeInsightFixtureTestCase {
  public void testEnsureReferencesAreRemoved() throws Exception {
    PsiClass aClass = myFixture.addClass("class Foo {public void bar(int i){}}");
    Project project = myFixture.getProject();
    RefManagerImpl refManager = new RefManagerImpl(project, new AnalysisScope(aClass.getContainingFile()), InspectionManager.getInstance(
      project).createNewGlobalContext(false));
    refManager.findAllDeclarations();
    List<RefElement> sortedElements = refManager.getSortedElements();

    RefElement refMethod = refManager.getReference(aClass.getMethods()[0]);
    List<RefEntity> children = refMethod.getChildren();
    ArrayList<RefElement> deletedRefs = new ArrayList<>();
    refManager.removeRefElement(refMethod, deletedRefs);
    assertTrue(deletedRefs.containsAll(children));
    assertTrue(deletedRefs.contains(refMethod));

    //check that table was not reinitialized due to full table traversal
    assertTrue(sortedElements == refManager.getSortedElements());
  }
}
