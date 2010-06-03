package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public class ExtractSuperClassTest extends CodeInsightTestCase {
  public void testFinalFieldInitialization() throws Exception {   // IDEADEV-19704
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("X", PsiClass.class),
           new RefactoringTestUtil.MemberDescriptor("x", PsiField.class));
  }

  public void testFieldInitializationWithCast() throws Exception {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("x", PsiField.class));
  }

  public void testParameterNameEqualsFieldName() throws Exception {    // IDEADEV-10629
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("a", PsiField.class));
  }

  public void testExtendsLibraryClass() throws Exception {
    doTest("Test", "TestSubclass");
  }

  public void testRequiredImportRemoved() throws Exception {
    doTest("foo.impl.B", "BImpl", new RefactoringTestUtil.MemberDescriptor("getInstance", PsiMethod.class));
  }

  public void testSubstituteGenerics() throws Exception {
    doTest("B", "AB");
  }

  public void testExtendsList() throws Exception {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("List", PsiClass.class));
  }

  public void testImportsCorruption() throws Exception {
    doTest("p1.A", "AA", new RefactoringTestUtil.MemberDescriptor("m1", PsiMethod.class));
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk15("mock 1.5");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @NonNls
  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/extractSuperClass/" + getTestName(true);
  }

  private void doTest(@NonNls final String className, @NonNls final String newClassName,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.5"));
    final VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiClass psiClass = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    assertNotNull(psiClass);
    final MemberInfo[] members = RefactoringTestUtil.findMembers(psiClass, membersToFind);
    ExtractSuperClassProcessor processor = new ExtractSuperClassProcessor(myProject,
                                                                          psiClass.getContainingFile().getContainingDirectory(),
                                                                          newClassName,
                                                                          psiClass, members,
                                                                          false,
                                                                          new DocCommentPolicy(DocCommentPolicy.ASIS));
    processor.run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, IdeaTestUtil.CVS_FILE_FILTER);
  }
}