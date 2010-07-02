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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Arrays;

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

  public void testConflictUsingPrivateMethod() throws Exception {
    doTest("Test", "TestSubclass",
           new String[] {"Method <b><code>Test.foo()</code></b> is private and will not be accessible from method <b><code>x()</code></b>."},
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  public void testConflictUsingPackageLocalMethod() throws Exception {
    doTest("a.Test", "TestSubclass",
           new String[] {"method <b><code>Sup.foo()</code></b> won't be accessible"},
           "b",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  public void testConflictUsingPackageLocalSuperClass() throws Exception {
    doTest("a.Test", "TestSubclass",
           new String[] {"class <b><code>a.Sup</code></b> won't be accessible from package <b><code>b</code></b>"},
           "b",
           new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testNoConflictUsingProtectedMethodFromSuper() throws Exception {
    doTest("Test", "TestSubclass",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
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
    return JavaSdkImpl.getMockJdk17();
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
    doTest(className, newClassName, null, membersToFind);
  }

  private void doTest(@NonNls final String className, @NonNls final String newClassName,
                      String[] conflicts,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) throws Exception {
    doTest(className, newClassName, conflicts, null, membersToFind);
  }

  private void doTest(@NonNls final String className,
                      @NonNls final String newClassName,
                      String[] conflicts,
                      String targetPackageName,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk14());
    final VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiClass psiClass = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    assertNotNull(psiClass);
    final MemberInfo[] members = RefactoringTestUtil.findMembers(psiClass, membersToFind);
    PsiDirectory targetDirectory;
    if (targetPackageName == null) {
      targetDirectory = psiClass.getContainingFile().getContainingDirectory();
    } else {
      final PsiPackage aPackage = myJavaFacade.findPackage(targetPackageName);
      assertNotNull(aPackage);
      targetDirectory = aPackage.getDirectories()[0];
    }
    ExtractSuperClassProcessor processor = new ExtractSuperClassProcessor(myProject,
                                                                          targetDirectory,
                                                                          newClassName,
                                                                          psiClass, members,
                                                                          false,
                                                                          new DocCommentPolicy<PsiComment>(DocCommentPolicy.ASIS));
    final PsiPackage targetPackage;
    if (targetDirectory != null) {
      targetPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
    }
    else {
      targetPackage = null;
    }
    final PsiClass superClass = psiClass.getExtendsListTypes().length > 0 ? psiClass.getSuperClass() : null;
    final MultiMap<PsiElement, String> conflictsMap =
      PullUpConflictsUtil.checkConflicts(members, psiClass, superClass, targetPackage, targetDirectory, new InterfaceContainmentVerifier() {
        public boolean checkedInterfacesContain(PsiMethod psiMethod) {
          return PullUpHelper.checkedInterfacesContain(Arrays.asList(members), psiMethod);
        }
      }, false);
    if (conflicts != null) {
      if (conflictsMap.isEmpty()) {
        fail("Conflicts were not detected");
      }
      final HashSet<String> expectedConflicts = new HashSet<String>(Arrays.asList(conflicts));
      final HashSet<String> actualConflicts = new HashSet<String>(conflictsMap.values());
      assertEquals(expectedConflicts.size(), actualConflicts.size());
      for (String actualConflict : actualConflicts) {
        if (!expectedConflicts.contains(actualConflict)) {
          fail("Unexpected conflict: " + actualConflict);
        }
      }
    } else if (!conflictsMap.isEmpty()) {
      fail("Unexpected conflicts!!!");
    }
    processor.run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, IdeaTestUtil.CVS_FILE_FILTER);
  }
}
