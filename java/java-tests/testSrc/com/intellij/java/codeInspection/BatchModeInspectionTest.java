/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefFile;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BatchModeInspectionTest extends LightCodeInsightFixtureTestCase {
  public void testEnsureReferencesAreRemoved() {
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
    assertSame(sortedElements, refManager.getSortedElements());
  }

  public void testPsiClassOwnerReferencesCollectedWhileGraphBuilding() {
    PsiClass aClass = myFixture.addClass("class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "class Bar { void m() { new Foo(); }}");
    Project project = myFixture.getProject();
    RefManagerImpl refManager =
      new RefManagerImpl(project, new AnalysisScope(project), InspectionManager.getInstance(project).createNewGlobalContext(false));
    refManager.findAllDeclarations();

    RefElement refClass = refManager.getReference(aClass);
    Collection<RefElement> fileReferences = refClass.getInReferences().stream().filter(x -> x instanceof RefFile).collect(Collectors.toList());
    RefElement referent = assertOneElement(fileReferences);
    RefFile groovyFile = assertInstanceOf(referent, RefFile.class);
    assertEquals("Bar.groovy", groovyFile.getName());
  }
}
