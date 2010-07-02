package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.io.File;

/**
 * @author yole
 */
public class InlineToAnonymousClassMultifileTest extends CodeInsightTestCase {
  public void testProtectedMember() throws Exception {   // IDEADEV-18738
    doTest("p1.SubjectWithSuper");
  }

  public void testImportForConstructor() throws Exception {   // IDEADEV-18714
    doTest("p1.ChildCtor");
  }

  public void testStaticImports() throws Exception {   // IDEADEV-18745
    doTest("p1.Inlined");
  }

  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineToAnonymousClass/multifile/" + getTestName(true);
  }

  private void doTest(String className) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
    final VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiClass classToInline = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
    InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(myProject, 
                                                                                    classToInline,
                                                                                    null, false, false, false);
    UsageInfo[] usages = processor.findUsages();
    MultiMap<PsiElement,String> conflicts = processor.getConflicts(usages);
    assertEquals(0, conflicts.size());
    processor.run();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, IdeaTestUtil.CVS_FILE_FILTER);
  }
}
