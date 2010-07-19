package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.copy.CopyClassesHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;

/**
 * @author yole
 */
public class CopyClassTest extends CodeInsightTestCase {
  private VirtualFile myRootDir;

  public void testReplaceAllOccurrences() throws Exception {
    doTest("Foo", "Bar");
  }

  public void testLibraryClass() throws Exception {  // IDEADEV-28791
    doTest("java.util.ArrayList", "Bar");
  }

  private void doTest(final String oldName, final String copyName) throws Exception {
    String root = JavaTestUtil.getJavaTestDataPath() + "/refactoring/copyClass/" + getTestName(true);

    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17("java 1.5"));
    myRootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);

    performAction(oldName, copyName);

    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    FileDocumentManager.getInstance().saveAllDocuments();

    VirtualFile fileAfter = myRootDir.findChild(copyName + ".java");
    VirtualFile fileExpected = myRootDir.findChild(copyName + ".java.expected");

    IdeaTestUtil.assertFilesEqual(fileExpected, fileAfter);
  }

  private void performAction(final String oldName, final String copyName) throws IncorrectOperationException {
    PsiClass oldClass = JavaPsiFacade.getInstance(myProject).findClass(oldName, ProjectScope.getAllScope(myProject));
    CopyClassesHandler.doCopyClasses(Collections.singletonMap(oldClass.getNavigationElement().getContainingFile(), new PsiClass[]{oldClass}), copyName, myPsiManager.findDirectory(myRootDir),
                                     myProject);
  }

  public void testPackageLocalClasses() throws Exception {
    doMultifileTest();
  }

  public void testPackageLocalMethods() throws Exception {
    doMultifileTest();
  }

  //copy all classes from p1 -> p2
  private void doMultifileTest() throws Exception {
    String root = JavaTestUtil.getJavaTestDataPath() + "/refactoring/copyClass/multifile/" + getTestName(true);
    String rootBefore = root + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    final HashMap<PsiFile, PsiClass[]> map = new HashMap<PsiFile, PsiClass[]>();
    final VirtualFile sourceDir = rootDir.findChild("p1");
    for (VirtualFile file : sourceDir.getChildren()) {
      final PsiFile psiFile = myPsiManager.findFile(file);
      if (psiFile instanceof PsiJavaFile) {
        map.put(psiFile, ((PsiJavaFile)psiFile).getClasses());
      }
    }

    final VirtualFile targetVDir = rootDir.findChild("p2");
    CopyClassesHandler.doCopyClasses(map, null, myPsiManager.findDirectory(targetVDir), myProject);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, IdeaTestUtil.CVS_FILE_FILTER);
  }
}
