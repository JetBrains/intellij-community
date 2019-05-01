// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewToolWindowFactory;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@SuppressWarnings({"HardCodedStringLiteral"})
public class NavigateFromSourceTest extends BaseProjectViewTestCase {
  public void testShowClassMembers() {
    useStandardProviders();
    final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(getPackageDirectory());
    sortClassesByName(classes);
    PsiClass psiClass = classes[0];

    final AbstractProjectViewPSIPane pane = myStructure.createPane();
    final PsiFile containingFile = psiClass.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();

    myStructure.checkNavigateFromSourceBehaviour(psiClass, virtualFile, pane);

    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: showClassMembers\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     [Class1]\n" +
                                                     "     Class2\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n"
      , true);

    changeClassTextAndTryToNavigate("class Class11 {}", (PsiJavaFile)containingFile, pane, "-Project\n" +
                                                                                           " -PsiDirectory: showClassMembers\n" +
                                                                                           "  -PsiDirectory: src\n" +
                                                                                           "   -PsiDirectory: com\n" +
                                                                                           "    -PsiDirectory: package1\n" +
                                                                                           "     -Class1.java\n" +
                                                                                           "      [Class11]\n" +
                                                                                           "     Class2\n" +
                                                                                           getRootFiles() +
                                                                                           " +External Libraries\n");

    changeClassTextAndTryToNavigate("class Class1 {}", (PsiJavaFile)containingFile, pane, "-Project\n" +
                                                                                          " -PsiDirectory: showClassMembers\n" +
                                                                                          "  -PsiDirectory: src\n" +
                                                                                          "   -PsiDirectory: com\n" +
                                                                                          "    -PsiDirectory: package1\n" +
                                                                                          "     [Class1]\n" +
                                                                                          "     Class2\n" +
                                                                                          getRootFiles() +
                                                                                          " +External Libraries\n");

    doTestMultipleSelection(pane, ((PsiJavaFile)containingFile).getClasses()[0]);
  }

  public void testAutoscrollFromSourceOnOpening() {
    final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(getPackageDirectory());
    PsiClass psiClass = classes[0];

    FileEditorManager.getInstance(getProject()).openFile(psiClass.getContainingFile().getVirtualFile(), true);

    ProjectView projectView = ProjectView.getInstance(getProject());

    ((ProjectViewImpl)projectView).setAutoscrollFromSource(true, ProjectViewPane.ID);

    ToolWindow toolWindow = ToolWindowManager.getInstance(getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW);

    new ProjectViewToolWindowFactory().createToolWindowContent(getProject(), toolWindow);

    projectView.changeView(ProjectViewPane.ID);
    UIUtil.dispatchAllInvocationEvents();

    JComponent component = ((ProjectViewImpl)projectView).getComponent();
    DataContext context = DataManager.getInstance().getDataContext(component);
    PsiElement element = CommonDataKeys.PSI_FILE.getData(context);
    assertEquals("Class1.java", ((PsiJavaFile)element).getName());
  }

  private static void doTestMultipleSelection(final AbstractProjectViewPSIPane pane, final PsiClass psiClass) {
    JTree tree = pane.getTree();
    int rowCount = tree.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      tree.addSelectionRow(i);
    }

    pane.select(psiClass, psiClass.getContainingFile().getVirtualFile(), true);

    assertEquals(8, tree.getSelectionCount());
  }

  private static void changeClassTextAndTryToNavigate(final String newClassString,
                                                      PsiJavaFile psiFile,
                                                      final AbstractProjectViewPSIPane pane,
                                                      final String expected) {
    PsiClass psiClass = psiFile.getClasses()[0];
    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    final JTree tree = pane.getTree();
    setBinaryContent(virtualFile, newClassString.getBytes(StandardCharsets.UTF_8));

    PlatformTestUtil.waitForAlarm(600);

    psiClass = psiFile.getClasses()[0];
    pane.select(psiClass, virtualFile, true);
    PlatformTestUtil.waitWhileBusy(tree);
    PlatformTestUtil.assertTreeEqual(tree, expected, true);
  }

  public void testSelectFileInArchiveUnderModuleGroup() throws IOException {
    Project project = getProject();
    VirtualFile root = getContentRoot();

    // create grouped module on top level to create ModuleGroupNode
    VirtualFile moduleRoot = WriteAction.computeAndWait(() -> root.getParent().createChildDirectory(project, "module"));
    Module module = createModule("group.module");
    PsiTestUtil.addContentRoot(module, moduleRoot);

    // move archive from default module to the created one
    VirtualFile archive = root.findChild("test.jar");
    WriteAction.runAndWait(() -> archive.move(project, moduleRoot));
    PsiTestUtil.addLibrary(module, archive.getPath()); // only libraries are expanded now

    AbstractProjectViewPSIPane pane = myStructure.createPane();
    // select archived file in the Project View
    VirtualFile file = JarFileSystem.getInstance().getJarRootForLocalFile(archive).findChild("Main.class");
    pane.select(PsiManager.getInstance(project).findFile(file), file, false);
    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -Group: group\n" +
                                                     "  -PsiDirectory: module\n" +
                                                     "   -test.jar\n" +
                                                     "    PsiDirectory: META-INF\n" +
                                                     "    [Main]\n" +
                                                     " PsiDirectory: selectFileInArchiveUnderModuleGroup\n" +
                                                     " group.module.iml\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n"
      , true);
  }
}
