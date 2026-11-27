package com.intellij.java.injection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaInjectedLanguageMultiFileTest {
  @NonNls private static final String BASE_PATH = "/injection/multiFile/";

  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    MyTestInjector testInjector = new MyTestInjector(fixture.getPsiManager());
    testInjector.injectAll(fixture.getTestRootDisposable());
  }

  private void doTest(String[] classNames, String newPackageName) throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    String root = fixture.getTestDataPath() + BASE_PATH + fixture.getTestName(true);

    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk17());
    String rootBefore = root + "/before";
    VirtualFile rootDir = fixture.createTestProjectStructure(rootBefore);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = StandardFileSystems.local().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    EdtTestUtil.runInEdtAndWait(() -> {
      performAction(classNames, newPackageName);
      PostprocessReformattingAspect.getInstance(fixture.getProject()).doPostponedFormatting();
      PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
    });
  }

  private void performAction(String[] classNames, String newPackageName) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    final PsiClass[] classes = new PsiClass[classNames.length];
    for(int i = 0; i < classes.length; i++){
      String className = classNames[i];
      classes[i] = fixture.getJavaFacade().findClass(className, GlobalSearchScope.allScope(project));
      assertNotNull(classes[i], "Class " + className + " not found");
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(newPackageName);
    assertNotNull(aPackage, "Package " + newPackageName + " not found");
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertThat(dirs).hasSize(1);

    PackageWrapper wrapper = PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dirs[0]));
    SingleSourceRootMoveDestination destination = new SingleSourceRootMoveDestination(wrapper, dirs[0]);
    new MoveClassesOrPackagesProcessor(project, classes, destination, true, true, null).run();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  @Test
  public void testMoveClass() throws Exception { doTest(new String[]{"x.y.C"}, "x"); }
}
