// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewToolWindowFactory;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.projectView.ProjectViewFixture;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JTree;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.intellij.projectView.ProjectViewFixture.projectViewFixture;
import static com.intellij.projectView.ProjectViewFixture.sortClassesByName;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
@SuppressWarnings({"HardCodedStringLiteral"})
public class NavigateFromSourceTest {
  private final TestFixture<ProjectViewFixture> projectViewFixture = projectViewFixture();

  @Test
  public void testShowClassMembers() throws IOException {
    ProjectViewFixture fixture = projectViewFixture.get();
    fixture.useStandardProviders();
    PsiClass psiClass = ReadAction.computeBlocking(() -> {
      final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(fixture.getPackageDirectory());
      sortClassesByName(classes);
      return classes[0];
    });

    final AbstractProjectViewPane pane = EdtTestUtil.runInEdtAndGet(() -> fixture.getStructure().createPane());
    final PsiFile containingFile = ReadAction.computeBlocking(() -> psiClass.getContainingFile());
    final VirtualFile virtualFile = containingFile.getVirtualFile();

    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.getStructure().checkNavigateFromSourceBehaviour(psiClass, virtualFile, pane);

      PlatformTestUtil.assertTreeEqual(pane.getTree(), """
                                         -Project
                                          -PsiDirectory: showClassMembers
                                           -PsiDirectory: src
                                            -PsiDirectory: com
                                             -PsiDirectory: package1
                                              [Class1]
                                              Class2
                                          +External Libraries
                                         """
        , true);
    });

    changeClassTextAndTryToNavigate("class Class11 {}", (PsiJavaFile)containingFile, pane, """
      -Project
       -PsiDirectory: showClassMembers
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           -Class1.java
            [Class11]
           Class2
       +External Libraries
      """);

    changeClassTextAndTryToNavigate("class Class1 {}", (PsiJavaFile)containingFile, pane, """
      -Project
       -PsiDirectory: showClassMembers
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           [Class1]
           Class2
       +External Libraries
      """);

    doTestMultipleSelection(pane, ReadAction.computeBlocking(() -> ((PsiJavaFile)containingFile).getClasses()[0]));
  }

  @Test
  public void testAutoscrollFromSourceOnOpening() {
    ProjectViewFixture fixture = projectViewFixture.get();
    PsiClass psiClass = ReadAction.computeBlocking(() -> {
      final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(fixture.getPackageDirectory());
      return classes[0];
    });

    EdtTestUtil.runInEdtAndWait(() -> {
      FileEditorManager.getInstance(fixture.getProject()).openFile(psiClass.getContainingFile().getVirtualFile(), true);

      ProjectView projectView = ProjectView.getInstance(fixture.getProject());

      ((ProjectViewImpl)projectView).setAutoscrollFromSource(true, ProjectViewPane.ID);

      ToolWindow toolWindow = ToolWindowManager.getInstance(fixture.getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW);

      new ProjectViewToolWindowFactory().createToolWindowContent(fixture.getProject(), toolWindow);

      projectView.changeView(ProjectViewPane.ID);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      JComponent component = ((ProjectViewImpl)projectView).getComponent();
      DataContext context = DataManager.getInstance().getDataContext(component);
      PsiElement element = CommonDataKeys.PSI_FILE.getData(context);
      assertThat(((PsiJavaFile)element).getName()).isEqualTo("Class1.java");
    });
  }

  private static void doTestMultipleSelection(AbstractProjectViewPane pane, PsiClass psiClass) {
    EdtTestUtil.runInEdtAndWait(() -> {
      JTree tree = pane.getTree();
      int rowCount = tree.getRowCount();
      for (int i = 0; i < rowCount; i++) {
        tree.addSelectionRow(i);
      }

      pane.select(psiClass, psiClass.getContainingFile().getVirtualFile(), true);

      assertThat(tree.getSelectionPaths()).hasSize(7);
    });
  }

  private static void changeClassTextAndTryToNavigate(String newClassString,
                                                      PsiJavaFile psiFile,
                                                      AbstractProjectViewPane pane,
                                                      String expected) throws IOException {
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass firstClass = psiFile.getClasses()[0];
      VirtualFile virtualFile = firstClass.getContainingFile().getVirtualFile();
      JTree tree = pane.getTree();
      WriteAction.runAndWait(() -> virtualFile.setBinaryContent(newClassString.getBytes(StandardCharsets.UTF_8)));

      PlatformTestUtil.waitForAlarm(600);

      PsiClass psiClass = psiFile.getClasses()[0];
      pane.select(psiClass, virtualFile, true);
      PlatformTestUtil.waitWhileBusy(tree);
      PlatformTestUtil.assertTreeEqual(tree, expected, true);
    });
  }

  @Test
  public void testSelectFileInArchiveUnderModuleGroup() throws IOException {
    ProjectViewFixture fixture = projectViewFixture.get();
    Project project = fixture.getProject();
    VirtualFile root = fixture.getContentRoot();

    // create grouped module on top level to create ModuleGroupNode
    VirtualFile moduleRoot = WriteAction.computeAndWait(() -> root.getParent().createChildDirectory(project, "module"));
    Module module = fixture.createModule("group.module");
    PsiTestUtil.addContentRoot(module, moduleRoot);

    // move archive from default module to the created one
    VirtualFile archive = root.findChild("test.jar");
    WriteAction.runAndWait(() -> archive.move(project, moduleRoot));
    PsiTestUtil.addLibrary(module, archive.getPath()); // only libraries are expanded now

    EdtTestUtil.runInEdtAndWait(() -> {
      AbstractProjectViewPane pane = fixture.getStructure().createPane();
      // select archived file in the Project View
      VirtualFile file = JarFileSystem.getInstance().getJarRootForLocalFile(archive).findChild("Main.class");
      pane.select(PsiManager.getInstance(project).findFile(file), file, false);
      PlatformTestUtil.waitWhileBusy(pane.getTree());
      PlatformTestUtil.assertTreeEqual(pane.getTree(), """
        -Project
         -Group: group
          -PsiDirectory: module
           -test.jar
            PsiDirectory: META-INF
            [Main]
         PsiDirectory: selectFileInArchiveUnderModuleGroup
         +External Libraries
        """, true);
    });
  }
}
