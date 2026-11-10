/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefFile;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class BatchModeInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testEnsureReferencesAreRemoved() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("class Foo {public void bar(int i){}}");
    Project project = myFixture.getProject();
    GlobalInspectionContext context = InspectionManager.getInstance(project).createNewGlobalContext();
    ((GlobalInspectionContextBase)context).initializeTools(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    ReadAction.runBlocking(() -> {
      RefManagerImpl refManager = new RefManagerImpl(project, new AnalysisScope(aClass.getContainingFile()), context);
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
    });
  }

  @Test
  public void testPsiClassOwnerReferencesCollectedWhileGraphBuilding() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "class Bar { void m() { new Foo(); }}");
    Project project = myFixture.getProject();
    GlobalInspectionContext context = InspectionManager.getInstance(project).createNewGlobalContext();
    ((GlobalInspectionContextBase)context).initializeTools(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    ReadAction.runBlocking(() -> {
      RefManagerImpl refManager = new RefManagerImpl(project, new AnalysisScope(project), context);
      refManager.findAllDeclarations();

      RefElement refClass = refManager.getReference(aClass);
      Collection<RefElement> fileReferences =
        ContainerUtil.filter(refClass.getInReferences(), x -> x instanceof RefFile);
      RefElement referent = assertThat(fileReferences).singleElement().actual();
      RefFile groovyFile = assertInstanceOf(RefFile.class, referent);
      assertEquals("Bar.groovy", groovyFile.getName());
    });
  }

  /** Ensures a canceled task cannot leave the RefManagerImpl waiting forever */
  @Test
  public void testCanceledTaskDoesNotBlockRefManager() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("batch.inspections.process.project.usages.in.parallel").setValue(true, myFixture.getTestRootDisposable());
    Registry.get("batch.inspections.visit.psi.in.parallel").setValue(true, myFixture.getTestRootDisposable());

    PsiClass aClass = myFixture.addClass("class Foo {}");
    Project project = myFixture.getProject();
    GlobalInspectionContext context = InspectionManager.getInstance(project).createNewGlobalContext();
    ((GlobalInspectionContextBase)context).initializeTools(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    RefManagerImpl refManager = ReadAction.computeBlocking(() -> new RefManagerImpl(project, new AnalysisScope(aClass.getContainingFile()), context));

    //Key point: inject something that throws PCE inside RefManagerImpl task processing
    refManager.registerGraphAnnotator(new RefGraphAnnotator() {
      @Override
      public void onInitialize(RefElement refElement) {
        throw new ProcessCanceledException();
      }
    });

    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(
      () -> ProgressManager.getInstance().runProcess(
        () -> ReadAction.computeBlocking(() -> {
          refManager.runInsideInspectionReadAction(refManager::findAllDeclarations);
          return null;
        }),
        indicator
      )
    );
    try {
      future.get(30, TimeUnit.SECONDS);
    }
    catch (TimeoutException e) {
      indicator.cancel();
      fail("A rejected reference-graph task must release the worker wait");
    }
  }
}
