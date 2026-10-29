package com.intellij.java.injection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.OpenProjectTaskBuilder;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaInjectedLanguageMultiFileTest extends JavaCodeInsightTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NonNls private static final String BASE_PATH = "/injection/multiFile/";

  @Override
  protected @NotNull OpenProjectTaskBuilder getOpenProjectOptions() {
    return super.getOpenProjectOptions().componentStoreLoadingEnabled(false);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(getTestRootDisposable());
  }

  private void doTest(String[] classNames, String newPackageName) throws Exception{
    String root = getTestDataPath()+ BASE_PATH + getTestName(true);

    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    String rootBefore = root + "/before";
    VirtualFile rootDir = createTestProjectStructure(rootBefore);

    performAction(classNames, newPackageName);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = StandardFileSystems.local().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    PostprocessReformattingAspect.getInstance(myProject).doPostponedFormatting();
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }

  private void performAction(String[] classNames, String newPackageName) {
    final PsiClass[] classes = new PsiClass[classNames.length];
    for(int i = 0; i < classes.length; i++){
      String className = classNames[i];
      classes[i] = myJavaFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Class " + className + " not found", classes[i]);
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertThat(dirs).hasSize(1);

    PackageWrapper wrapper = PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dirs[0]));
    SingleSourceRootMoveDestination destination = new SingleSourceRootMoveDestination(wrapper, dirs[0]);
    new MoveClassesOrPackagesProcessor(myProject, classes, destination, true, true, null).run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  public void testMoveClass() throws Exception { doTest(new String[]{"x.y.C"}, "x"); }
}
