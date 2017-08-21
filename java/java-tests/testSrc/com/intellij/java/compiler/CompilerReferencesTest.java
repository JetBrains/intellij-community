/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.compiler;

import com.intellij.JavaTestUtil;
import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SkipSlowTestLocally
public class CompilerReferencesTest extends CompilerReferencesTestBase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/compiler/bytecodeReferences/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    installCompiler();
  }

  public void testIsNotReady() {
    myFixture.configureByFile(getName() + "/Foo.java");
    assertNull(getReferentFilesForElementUnderCaret());
  }

  public void testSimpleUsagesInFullyCompiledProject() {
    myFixture.configureByFiles(getName() + "/Foo.java", getName() + "/Bar.java", getName() + "/Baz.java", getName() + "/FooImpl.java");
    rebuildProject();

    final Set<VirtualFile> referents = getReferentFilesForElementUnderCaret();
    assertNotNull(referents);
    final Set<String> filesWithReferences = referents.stream().map(VirtualFile::getName).collect(Collectors.toSet());

    assertEquals(filesWithReferences, ContainerUtil.set("Baz.java", "Foo.java", "FooImpl.java"));
    myFixture.addFileToProject("SomeModification.java", "");
    assertNull(getReferentFilesForElementUnderCaret());
  }

  public void testLambda() {
    myFixture.configureByFiles(getName() + "/Foo.java", getName() + "/FooImpl.java", getName() + "/Bar.java", getName() + "/BarRef.java");
    rebuildProject();
    final CompilerDirectHierarchyInfo funExpressions = getFunctionalExpressionsForElementUnderCaret();
    List<PsiFunctionalExpression> funExprs = funExpressions.getHierarchyChildren().map(PsiFunctionalExpression.class::cast).collect(Collectors.toList());
    assertSize(2, funExprs);
  }

  public void testInnerFunExpressions() {
    myFixture.configureByFiles(getName() + "/Foo.java");
    rebuildProject();
    List<PsiFunctionalExpression> funExpressions =
      getFunExpressionsFor(myFixture.getJavaFacade().findClass(CommonClassNames.JAVA_LANG_RUNNABLE))
        .getHierarchyChildren()
        .map(PsiFunctionalExpression.class::cast)
        .collect(Collectors.toList());
    assertSize(6, funExpressions);

    Set<String> funTypeNames = funExpressions
      .stream()
      .map(PsiElement::getParent)
      .map(PsiVariable.class::cast)
      .map(PsiVariable::getType)
      .map(PsiUtil::resolveClassInType)
      .filter(Objects::nonNull)
      .map(PsiClass::getQualifiedName)
      .collect(Collectors.toSet());
    String funTypeName = assertOneElement(funTypeNames);
    assertEquals(CommonClassNames.JAVA_LANG_RUNNABLE, funTypeName);
  }

  public void testHierarchy() {
    myFixture.configureByFiles(getName() + "/Foo.java", getName() + "/FooImpl.java", getName() + "/Bar.java", getName() + "/Baz.java", getName() + "/Test.java");
    rebuildProject();
    CompilerDirectHierarchyInfo directInheritorInfo = getHierarchyForElementUnderCaret();

    Collection<PsiClass> inheritors = directInheritorInfo.getHierarchyChildren().map(PsiClass.class::cast).collect(Collectors.toList());
    assertSize(6, inheritors);
    for (PsiClass inheritor : inheritors) {
      if (inheritor instanceof PsiAnonymousClass) {
        assertOneOf(inheritor.getTextOffset(), 58, 42, 94);
      } else {
        assertOneOf(inheritor.getName(), "FooImpl", "FooImpl2", "FooInsideMethodImpl");
      }
    }
  }

  public void testHierarchyOfLibClass() {
    myFixture.configureByFiles(getName() + "/Foo.java");
    rebuildProject();
    CompilerDirectHierarchyInfo directInheritorInfo = getDirectInheritorsFor(myFixture.getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_LIST));
    PsiClass inheritor = assertOneElement(directInheritorInfo.getHierarchyChildren().map(PsiClass.class::cast).collect(Collectors.toList()));
    assertEquals("Foo.ListImpl", inheritor.getQualifiedName());
  }

  public void testExtensionRename() {
    final PsiFile file = myFixture.configureByFiles(getName() + "/Bar.java", getName() + "/Foo.txt")[1];
    rebuildProject();
    assertOneElement(getReferentFilesForElementUnderCaret());
    myFixture.renameElement(file, "Foo.java");
    final PsiClass foo = myFixture.findClass("Foo");
    assertNotNull(foo);
    final CompilerReferenceServiceImpl compilerReferenceService = (CompilerReferenceServiceImpl) CompilerReferenceService
      .getInstance(myFixture.getProject());
    compilerReferenceService.getScopeWithoutCodeReferences(foo);
    assertOneElement(compilerReferenceService.getDirtyScopeHolder().getAllDirtyModulesForTest());
  }

  public void testReverseExtensionRename() {
    final PsiFile file = myFixture.configureByFiles(getName() + "/Bar.java", getName() + "/Foo.java")[1];
    rebuildProject();
    assertSize(2, getReferentFilesForElementUnderCaret());
    myFixture.renameElement(file, "Foo.txt");
    assertEquals("Bar.java", assertOneElement(getReferentFilesForElementUnderCaret()).getName());
  }

  private CompilerDirectHierarchyInfo getHierarchyForElementUnderCaret() {
    final PsiElement atCaret = myFixture.getElementAtCaret();
    assertNotNull(atCaret);
    final PsiClass classAtCaret = PsiTreeUtil.getParentOfType(atCaret, PsiClass.class, false);
    assertNotNull(classAtCaret);
    return getDirectInheritorsFor(classAtCaret);
  }

  private CompilerDirectHierarchyInfo getFunctionalExpressionsForElementUnderCaret() {
    final PsiElement atCaret = myFixture.getElementAtCaret();
    assertNotNull(atCaret);
    final PsiClass classAtCaret = PsiTreeUtil.getParentOfType(atCaret, PsiClass.class, false);
    assertNotNull(classAtCaret);
    return getFunExpressionsFor(classAtCaret);
  }

  private CompilerDirectHierarchyInfo getDirectInheritorsFor(PsiClass classAtCaret) {
    return CompilerReferenceService.getInstance(myFixture.getProject()).getDirectInheritors(classAtCaret,
                                                                                            assertInstanceOf(classAtCaret.getUseScope(), GlobalSearchScope.class),
                                                                                            assertInstanceOf(classAtCaret.getUseScope(), GlobalSearchScope.class),
                                                                                            StdFileTypes.JAVA);
  }

  private CompilerDirectHierarchyInfo getFunExpressionsFor(PsiClass classAtCaret) {
    return CompilerReferenceService.getInstance(myFixture.getProject()).getFunExpressions(classAtCaret,
                                                                                          assertInstanceOf(classAtCaret.getUseScope(), GlobalSearchScope.class),
                                                                                          assertInstanceOf(classAtCaret.getUseScope(), GlobalSearchScope.class),
                                                                                          StdFileTypes.JAVA);
  }

  private Set<VirtualFile> getReferentFilesForElementUnderCaret() {
    final PsiElement atCaret = myFixture.getElementAtCaret();
    assertNotNull(atCaret);
    final PsiMember memberAtCaret = PsiTreeUtil.getParentOfType(atCaret, PsiMember.class, false);
    assertNotNull(memberAtCaret);
    return ((CompilerReferenceServiceImpl)CompilerReferenceService.getInstance(myFixture.getProject())).getReferentFiles(memberAtCaret);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }
}
