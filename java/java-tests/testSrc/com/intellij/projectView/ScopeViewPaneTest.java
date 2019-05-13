// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewSharedSettings;
import com.intellij.ide.scopeView.ScopeTreeViewPanel;
import com.intellij.ide.scopeView.ScopeViewPane;
import com.intellij.idea.Bombed;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.ProjectFilesScope;
import com.intellij.psi.search.scope.ProjectProductionScope;
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Calendar;

public class ScopeViewPaneTest extends TestSourceBasedTestCase {
  private static final int DELAY = 200;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    restoreProjectViewDefaultSettings();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      restoreProjectViewDefaultSettings();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private static void restoreProjectViewDefaultSettings() {
    XmlSerializerUtil.copyBean(new ProjectViewSharedSettings(), ProjectViewSharedSettings.Companion.getInstance());
  }

  @Bombed(user = "SAM", month = Calendar.JUNE, day = 1)
  public void testStructure() {
    final Project project = getProject();
    final ProjectViewImpl view = (ProjectViewImpl)ProjectView.getInstance(project);
    changeView(view, ProjectProductionScope.INSTANCE);
    final AbstractProjectViewPane pane = view.getCurrentProjectViewPane();
    final PsiDirectory src = getContentDirectory().findSubdirectory("src");
    assert src != null;
    final PsiDirectory directory1 = src.findSubdirectory("package1");
    assert directory1 != null;
    final PsiDirectory directory2 = directory1.findSubdirectory("package2");
    assert directory2 != null;
    final PsiDirectory directory = directory2.findSubdirectory("package3");
    assert directory != null;
    final PsiFile psiFile = directory.findFile("Test3.java");
    assert psiFile != null;
    pane.select(psiFile, psiFile.getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1\n" +
                                     "    -package2/package3\n" +
                                     "     Test3\n" +
                                     "    Test1\n" +
                                     "   Test\n", false);

    final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
    assert psiPackage != null;
    WriteCommandAction.runWriteCommandAction(null, () -> psiPackage.getClasses()[0].delete());

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1\n" +
                                     "    Test1\n" +
                                     "   Test\n", false);


    final PsiJavaFile psiFile1 = (PsiJavaFile)directory1.findFile("Test1.java");
    assert psiFile1 != null;
    final PsiClass aClass = psiFile1.getClasses()[0];
    new RenameProcessor(myProject, aClass, "Test111", false, false).run();

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1\n" +
                                     "    Test111\n" +
                                     "   Test\n", false);

    final PsiClass aClass1 = JavaDirectoryService.getInstance().createClass(directory1, "Class");
    pane.select(aClass1, aClass1.getContainingFile().getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1\n" +
                                     "    Class\n" +
                                     "    Test111\n" +
                                     "   Test\n", false);

    new MoveClassesOrPackagesProcessor(myProject,
                                       new PsiElement[]{aClass1},
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService
                                                                                                   .getInstance()
                                                                                                   .getPackage(directory)),
                                                                           directory),
                                       false, false, null).run();

    pane.select(aClass1, aClass1.getContainingFile().getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1\n" +
                                     "    -package2/package3\n" +
                                     "     Class\n" +
                                     "    Test111\n" +
                                     "   Test\n", false);

    final PsiFile textFile = WriteCommandAction.runWriteCommandAction(null, (Computable<PsiFile>)() -> directory2.createFile("t.txt"));
    pane.select(textFile, textFile.getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1\n" +
                                     "    -package2\n" +
                                     "     -package3\n" +
                                     "      Class\n" +
                                     "     t.txt\n" +
                                     "    Test111\n" +
                                     "   Test\n", false);

    new MoveFilesOrDirectoriesProcessor(myProject, new PsiElement[]{textFile}, directory, false, false, null, null).run();

    pane.select(textFile, textFile.getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1\n" +
                                     "    -package2/package3\n" +
                                     "     Class\n" +
                                     "     t.txt\n" +
                                     "    Test111\n" +
                                     "   Test\n", false);

    final PsiDirectory emptyDir = WriteCommandAction.runWriteCommandAction(null,
                                                                           (Computable<PsiDirectory>)() -> directory2.createSubdirectory("emptyDir"));
    final IdeView data = (IdeView)pane.getData(LangDataKeys.IDE_VIEW.getName());
    assert data != null;
    data.selectElement(emptyDir);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1\n" +
                                     "    -package2\n" +
                                     "     emptyDir\n" +
                                     "     -package3\n" +
                                     "      Class\n" +
                                     "      t.txt\n" +
                                     "    Test111\n" +
                                     "   Test\n", false);

    Disposer.dispose(pane);
  }

  public void testInScope() {
    ScopeTreeViewPanel pane = null;
    try {
      final NamedScope namedScope = new NamedScope("sc", new FilePatternPackageSet(null, "*/Test3*"));
      pane = new ScopeTreeViewPanel(getProject()){
        @Override
        protected NamedScope getCurrentScope() {
          return namedScope;
        }
      };
      pane.initListeners();

      final ProjectViewImpl view = (ProjectViewImpl)ProjectView.getInstance(getProject());
      changeView(view, ProjectFilesScope.INSTANCE);
      view.setHideEmptyPackages(ScopeViewPane.ID, true);

      pane.refreshScope(namedScope);

      final PsiClass aClass = getJavaFacade().findClass("package1.package2.package3.Test3");
      assert aClass != null;
      pane.selectNode(aClass, aClass.getContainingFile(), true);

      PlatformTestUtil.waitForAlarm(DELAY);
      PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                       "-Root\n" +
                                       " -structure\n" +
                                       "  -src\n" +
                                       "   -package1/package2/package3\n" +
                                       "    Test3\n", false);

      //exclude from scope
      new RenameProcessor(myProject, aClass, "ATest3", false, false).run();

      PlatformTestUtil.waitForAlarm(DELAY);
      PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                       "Root\n", false);

      //restore in scope
      new RenameProcessor(myProject, aClass, "Test3", false, false).run();
      pane.selectNode(aClass, aClass.getContainingFile(), true);
      PlatformTestUtil.waitForAlarm(DELAY);
      PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                             "-Root\n" +
                                             " -structure\n" +
                                             "  -src\n" +
                                             "   -package1/package2/package3\n" +
                                             "    Test3\n", false);


      final PsiPackage package3 = JavaPsiFacade.getInstance(getProject()).findPackage("package1.package2.package3");
      final PsiPackage package1 = JavaPsiFacade.getInstance(getProject()).findPackage("package1");

      new MoveClassesOrPackagesProcessor(myProject,
                                         new PsiElement[]{package3},
                                         new SingleSourceRootMoveDestination(PackageWrapper.create(package1),
                                                                             package1.getDirectories()[0]),
                                         false, false, null).run();

      pane.selectNode(aClass, aClass.getContainingFile(), true);
      PlatformTestUtil.waitForAlarm(DELAY);
      PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                       "-Root\n" +
                                       " -structure\n" +
                                       "  -src\n" +
                                       "   -package1/package3\n" +
                                       "    Test3\n", false);

      final PsiPackage package13 = JavaPsiFacade.getInstance(getProject()).findPackage("package1.package3");
      final PsiPackage package123 = JavaPsiFacade.getInstance(getProject()).findPackage("package1.package2");
      new MoveClassesOrPackagesProcessor(myProject,
                                         new PsiElement[]{package13},
                                         new SingleSourceRootMoveDestination(PackageWrapper.create(package123),
                                                                             package123.getDirectories()[0]),
                                         false, false, null).run();

      pane.selectNode(aClass, aClass.getContainingFile(), true);
      PlatformTestUtil.waitForAlarm(DELAY);
      PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                       "-Root\n" +
                                       " -structure\n" +
                                       "  -src\n" +
                                       "   -package1/package2/package3\n" +
                                       "    Test3\n", false);
    }
    finally {
      if (pane != null) {
        Disposer.dispose(pane);
      }
    }
  }

  public void testInScopeNoCompacting() {
     ScopeTreeViewPanel pane = null;
     try {
       final NamedScope namedScope = new NamedScope("sc", new FilePatternPackageSet(null, "*/Test3*"));
       pane = new ScopeTreeViewPanel(getProject()){
         @Override
         protected NamedScope getCurrentScope() {
           return namedScope;
         }
       };
       pane.initListeners();

       final ProjectViewImpl view = (ProjectViewImpl)ProjectView.getInstance(getProject());
       changeView(view, ProjectFilesScope.INSTANCE);
       view.setHideEmptyPackages(ScopeViewPane.ID, false);

       pane.refreshScope(namedScope);

       final PsiClass aClass = getJavaFacade().findClass("package1.package2.package3.Test3");
       assert aClass != null;
       pane.selectNode(aClass, aClass.getContainingFile(), true);

       PlatformTestUtil.waitForAlarm(DELAY);
       PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                        "-Root\n" +
                                        " -structure\n" +
                                        "  -src\n" +
                                        "   -package1\n" +
                                        "    -package2\n" +
                                        "     -package3\n" +
                                        "      Test3\n", false);

       //exclude from scope
       new RenameProcessor(myProject, aClass, "ATest3", false, false).run();

       PlatformTestUtil.waitForAlarm(DELAY);
       PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                        "Root\n", false);

       //restore in scope
       new RenameProcessor(myProject, aClass, "Test3", false, false).run();
       pane.selectNode(aClass, aClass.getContainingFile(), true);
       PlatformTestUtil.waitForAlarm(DELAY);
       PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                              "-Root\n" +
                                              " -structure\n" +
                                              "  -src\n" +
                                              "   -package1\n" +
                                              "    -package2\n" +
                                              "     -package3\n" +
                                              "      Test3\n", false);


       final PsiPackage package3 = JavaPsiFacade.getInstance(getProject()).findPackage("package1.package2.package3");
       final PsiPackage package1 = JavaPsiFacade.getInstance(getProject()).findPackage("package1");

       new MoveClassesOrPackagesProcessor(myProject,
                                          new PsiElement[]{package3},
                                          new SingleSourceRootMoveDestination(PackageWrapper.create(package1),
                                                                              package1.getDirectories()[0]),
                                          false, false, null).run();

       pane.selectNode(aClass, aClass.getContainingFile(), true);
       PlatformTestUtil.waitForAlarm(DELAY);
       PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                        "-Root\n" +
                                        " -structure\n" +
                                        "  -src\n" +
                                        "   -package1\n" +
                                        "    -package3\n" +
                                        "     Test3\n", false);

       final PsiPackage package13 = JavaPsiFacade.getInstance(getProject()).findPackage("package1.package3");
       final PsiPackage package123 = JavaPsiFacade.getInstance(getProject()).findPackage("package1.package2");
       new MoveClassesOrPackagesProcessor(myProject,
                                          new PsiElement[]{package13},
                                          new SingleSourceRootMoveDestination(PackageWrapper.create(package123),
                                                                              package123.getDirectories()[0]),
                                          false, false, null).run();

       pane.selectNode(aClass, aClass.getContainingFile(), true);
       PlatformTestUtil.waitForAlarm(DELAY);
       PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                        "-Root\n" +
                                        " -structure\n" +
                                        "  -src\n" +
                                        "   -package1\n" +
                                        "    -package2\n" +
                                        "     -package3\n" +
                                        "      Test3\n", false);
     }
     finally {
       if (pane != null) {
         Disposer.dispose(pane);
       }
     }
   }

  @Bombed(user = "SAM", year = 2020, month = Calendar.JANUARY, day = 1)
  public void testCompactEmptyDirectories() {
    final Project project = getProject();
    final ProjectViewImpl view = (ProjectViewImpl)ProjectView.getInstance(project);
    changeView(view, ProjectFilesScope.INSTANCE);
    view.setHideEmptyPackages(ScopeViewPane.ID, true);
    final AbstractProjectViewPane pane = view.getCurrentProjectViewPane();
    final PsiDirectory src = getContentDirectory().findSubdirectory("src");
    assert src != null;
    final PsiDirectory directory1 = src.findSubdirectory("package1");
    assert directory1 != null;
    final PsiDirectory directory2 = directory1.findSubdirectory("package2");
    assert directory2 != null;
    final PsiDirectory directory = directory2.findSubdirectory("package3");
    assert directory != null;

    final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory1);
    assert psiPackage != null;

    WriteCommandAction.runWriteCommandAction(null, () -> psiPackage.getClasses()[0].delete());


    final PsiFile psiFile3 = directory.findFile("Test3.java");
    assert psiFile3 != null;
    pane.select(psiFile3, psiFile3.getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1/package2/package3\n" +
                                     "    Test3\n" +
                                     "   Test\n", false);

    final PsiJavaFile psiFile = (PsiJavaFile)src.findFile("Test.java");
    assert psiFile != null;
    final PsiClass aClass111 = psiFile.getClasses()[0];
    new RenameProcessor(myProject, aClass111, "Test111", false, false).run();

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1/package2/package3\n" +
                                     "    Test3\n" +
                                     "   Test111\n", false);

    final PsiClass aClass = JavaDirectoryService.getInstance().createClass(directory2, "Class");
    pane.select(aClass, aClass.getContainingFile().getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1/package2\n" +
                                     "    +package3\n" +
                                     "    Class\n" +
                                     "   Test111\n", false);

    view.setFlattenPackages(ScopeViewPane.ID, true);
    pane.select(aClass, aClass.getContainingFile().getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Root\n" +
                                     " -structure\n" +
                                     "  -src\n" +
                                     "   -package1/package2\n" +
                                     "    Class\n" +
                                     "   +package1/package2/package3\n" +
                                     "   Test111\n", false);

    Disposer.dispose(pane);
  }

  @Bombed(user = "SAM", year = 2020, month = Calendar.JANUARY, day = 1)
  public void testFlattenPackages() {
    final Project project = getProject();
    final ProjectViewImpl view = (ProjectViewImpl)ProjectView.getInstance(project);
    changeView(view, ProjectFilesScope.INSTANCE);
    view.setFlattenPackages(ScopeViewPane.ID, true);
    final AbstractProjectViewPane pane = view.getCurrentProjectViewPane();
    final PsiDirectory src = getContentDirectory().findSubdirectory("src");
    assert src != null;
    final PsiDirectory directory1 = src.findSubdirectory("package1");
    assert directory1 != null;
    final PsiDirectory directory2 = directory1.findSubdirectory("package2");
    assert directory2 != null;
    final PsiDirectory directory = directory2.findSubdirectory("package3");
    assert directory != null;
    final PsiFile psiFile = directory.findFile("Test3.java");
    assert psiFile != null;
    pane.select(psiFile, psiFile.getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Root\n" +
                                                     " -structure\n" +
                                                     "  -src\n" +
                                                     "   +package1\n" +
                                                     "   -package1/package2/package3\n" +
                                                     "    Test3\n" +
                                                     "   Test\n", false);

    final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
    assert psiPackage != null;
    ApplicationManager.getApplication().runWriteAction(() -> psiPackage.getClasses()[0].delete());


    final PsiJavaFile psiFile1 = (PsiJavaFile)directory1.findFile("Test1.java");
    assert psiFile1 != null;
    pane.select(psiFile1, psiFile1.getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Root\n" +
                                                     " -structure\n" +
                                                     "  -src\n" +
                                                     "   -package1\n" +
                                                     "    Test1\n" +
                                                     "   Test\n", false);

    final PsiClass aClass = psiFile1.getClasses()[0];
    new RenameProcessor(myProject, aClass, "Test111", false, false).run();

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Root\n" +
                                                     " -structure\n" +
                                                     "  -src\n" +
                                                     "   -package1\n" +
                                                     "    Test111\n" +
                                                     "   Test\n", false);

    final PsiClass aClass1 = JavaDirectoryService.getInstance().createClass(directory1, "Class");
    pane.select(aClass1, aClass1.getContainingFile().getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Root\n" +
                                                     " -structure\n" +
                                                     "  -src\n" +
                                                     "   -package1\n" +
                                                     "    Class\n" +
                                                     "    Test111\n" +
                                                     "   Test\n", false);

    view.setHideEmptyPackages(ScopeViewPane.ID, true);
    pane.select(aClass1, aClass1.getContainingFile().getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Root\n" +
                                                     " -structure\n" +
                                                     "  -src\n" +
                                                     "   -package1\n" +
                                                     "    Class\n" +
                                                     "    Test111\n" +
                                                     "   Test\n", false);

    Disposer.dispose(pane);
  }

  @Override
  protected String getTestPath() {
    return "packageSet";
  }

  @NotNull
  @Override
  protected String getTestDirectoryName() {
    return "structure";
  }

  private static void changeView(@NotNull ProjectView view, @NotNull NamedScope scope) {
    AbstractProjectViewPane pane = view.getProjectViewPaneById(ScopeViewPane.ID);
    for (String id : pane.getSubIds()) {
      if (id.startsWith(scope.toString())) {
        view.changeViewCB(ScopeViewPane.ID, id);
        assertSame("view not changed", pane, view.getCurrentProjectViewPane());
        return;
      }
    }
    fail("scope not found: " + scope);
  }
}
