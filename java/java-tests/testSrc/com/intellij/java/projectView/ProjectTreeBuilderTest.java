// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.structureView.impl.java.JavaInheritedMembersNodeProvider;
import com.intellij.ide.structureView.impl.java.KindSorter;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.ProjectViewFixture;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.concurrency.Promise;
import org.junit.jupiter.api.Test;

import javax.swing.tree.TreePath;

import static com.intellij.projectView.ProjectViewFixture.projectViewFixture;
import static com.intellij.projectView.ProjectViewFixture.sortClassesByName;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ProjectTreeBuilderTest {
  private final TestFixture<ProjectViewFixture> projectViewFixture = projectViewFixture();

  @Test
  public void testStandardProviders() {
    ProjectViewFixture fixture = projectViewFixture.get();
    fixture.getStructure().setProviders();

    final PsiClass aClass = ReadAction.computeBlocking(() -> JavaDirectoryService.getInstance().getClasses(fixture.getPackageDirectory())[0]);

    PsiFile element = ReadAction.computeBlocking(() -> aClass.getContainingFile());

    EdtTestUtil.runInEdtAndWait(
      () -> fixture.getStructure().checkNavigateFromSourceBehaviour(element, element.getVirtualFile(), fixture.getStructure().createPane()));
  }

  @Test
  public void testShowClassMembers() throws IncorrectOperationException {
    ProjectViewFixture fixture = projectViewFixture.get();
    fixture.getStructure().setShowMembers(true);
    fixture.useStandardProviders();
    PsiElement[] elements = ReadAction.computeBlocking(() -> {
      final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(fixture.getPackageDirectory());
      sortClassesByName(classes);
      PsiClass aClass = classes[1];
      PsiClass innerClass1 = aClass.getInnerClasses()[0];
      PsiClass innerClass12 = innerClass1.getInnerClasses()[0];
      PsiClass innerClass13 = innerClass12.getInnerClasses()[0];
      PsiClass innerClass14 = innerClass13.getInnerClasses()[0];
      PsiClass innerClass15 = innerClass14.getInnerClasses()[0];

      PsiClass innerClass2 = aClass.getInnerClasses()[1];
      PsiClass innerClass21 = innerClass2.getInnerClasses()[0];
      PsiClass innerClass23 = innerClass21.getInnerClasses()[0];
      PsiClass innerClass24 = innerClass23.getInnerClasses()[0];

      PsiField innerClass1Field = innerClass14.getFields()[0];
      PsiField innerClass2Field = innerClass24.getFields()[0];
      return new PsiElement[]{aClass, innerClass15.getFields()[0], innerClass1Field, innerClass2Field};
    });
    PsiClass aClass = (PsiClass)elements[0];
    PsiElement innerClass15Field = elements[1];
    PsiElement innerClass1Field = elements[2];
    PsiElement innerClass2Field = elements[3];

    EdtTestUtil.runInEdtAndWait(() -> {
      final AbstractProjectViewPane pane = fixture.getStructure().createPane();

      fixture.getStructure().checkNavigateFromSourceBehaviour(innerClass2Field, innerClass2Field.getContainingFile().getVirtualFile(), pane);

      PlatformTestUtil.assertTreeEqual(pane.getTree(), """
        -Project
         -PsiDirectory: showClassMembers
          -PsiDirectory: src
           -PsiDirectory: com
            -PsiDirectory: package1
             +Class1
             -Class2
              +InnerClass1
              -InnerClass2
               -InnerClass22
                -InnerClass23
                 -InnerClass24
                  +InnerClass25
                  myFieldToSelect: int
                 myInnerClassField: int
                myInnerClassField: int
               myInnerClassField: int
              getValue(): int
              myField1: boolean
              myField2: boolean
              myField3: boolean
              myField4: boolean
         +External Libraries
        """
      );

      assertFalse(ProjectViewTestUtil.isExpanded(innerClass15Field, pane));
      assertFalse(ProjectViewTestUtil.isExpanded(innerClass1Field, pane));
      assertTrue(ProjectViewTestUtil.isExpanded(innerClass2Field, pane));
    });

    EdtTestUtil.runInEdtAndWait(() -> {
      VirtualFile virtualFile = aClass.getContainingFile().getVirtualFile();
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(fixture.getProject());
      FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
      StructureViewComponent svc = (StructureViewComponent)fileEditors[0].getStructureViewBuilder()
        .createStructureView(fileEditors[0], fixture.getProject());
      svc.setActionActive(KindSorter.ID, true);
      Disposer.register(fixture.getTestRootDisposable(), svc);
      TreeUtil.collapseAll(svc.getTree(), -1);
      fileEditorManager.closeFile(virtualFile);

      Promise<TreePath> select = svc.select(innerClass2Field, true);
      PlatformTestUtil.waitForPromise(select);

      String expected = """
        -Class2.java
         -Class2
          +InnerClass1
          -InnerClass2
           -InnerClass22
            -InnerClass23
             -InnerClass24
              +InnerClass25
              myFieldToSelect: int
             myInnerClassField: int
            myInnerClassField: int
           myInnerClassField: int
          getValue(): int
          myField1: boolean
          myField2: boolean
          myField3: boolean
          myField4: boolean
        """;

      PlatformTestUtil.assertTreeEqual(svc.getTree(), expected);
      Disposer.dispose(svc);

      FileEditor fileEditor = fileEditors[0];
      StructureViewComponent svc2 = (StructureViewComponent)fileEditor.getStructureViewBuilder().createStructureView(fileEditor, fixture.getProject());
      Disposer.register(fixture.getTestRootDisposable(), svc2);
      svc2.setActionActive(JavaInheritedMembersNodeProvider.ID, false);
      PlatformTestUtil.waitWhileBusy(svc2.getTree());
      PlatformTestUtil.assertTreeEqual(svc2.getTree(), expected);
    });
  }
}
