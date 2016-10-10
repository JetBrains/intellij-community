/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.AbstractCompilerAwareTest;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@SkipSlowTestLocally
public class CompilerReferencesTest extends AbstractCompilerAwareTest {
  private boolean myDefaultEnableState;

  @Override
  public void setUp() throws Exception {
    myDefaultEnableState = CompilerReferenceService.IS_ENABLED_KEY.asBoolean();
    CompilerReferenceService.IS_ENABLED_KEY.setValue(true);
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    CompilerReferenceService.IS_ENABLED_KEY.setValue(myDefaultEnableState);
    super.tearDown();
  }

  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/compiler/bytecodeReferences/";
  }

  public void testIsNotReady() {
    myFixture.configureByFile(getName() + "/Foo.java");
    assertNull(getReferentFilesForElementUnderCaret(JavaBaseCompilerSearchAdapter.INSTANCE));
  }

  public void testSimpleUsagesInFullyCompiledProject() {
    myFixture.configureByFiles(getName() + "/Foo.java", getName() + "/Bar.java", getName() + "/Baz.java", getName() + "/FooImpl.java");
    rebuildProject();

    final Set<VirtualFile> referents = getReferentFilesForElementUnderCaret(JavaBaseCompilerSearchAdapter.INSTANCE);
    assertNotNull(referents);
    final Set<String> filesWithReferences = referents.stream().map(VirtualFile::getName).collect(Collectors.toSet());

    assertEquals(filesWithReferences, ContainerUtil.set("Baz.java", "Foo.java", "FooImpl.java"));
    myFixture.addFileToProject("SomeModification.java", "");
    assertNull(getReferentFilesForElementUnderCaret(JavaBaseCompilerSearchAdapter.INSTANCE));
  }

  public void testLambda() {
    myFixture.configureByFiles(getName() + "/Foo.java", getName() + "/FooImpl.java", getName() + "/Bar.java", getName() + "/BarRef.java");
    rebuildProject();
    final Set<VirtualFile> referents = getReferentFilesForElementUnderCaret(JavaFunctionalExpressionCompilerSearchAdapter.INSTANCE);
    assertNotNull(referents);
    final Set<String> filesWithReferences = referents.stream().map(VirtualFile::getName).collect(Collectors.toSet());
    assertEquals(filesWithReferences, ContainerUtil.set("Bar.java", "BarRef.java"));
  }

  public void testHierarchy() {
    myFixture.configureByFiles(getName() + "/Foo.java", getName() + "/FooImpl.java", getName() + "/Bar.java", getName() + "/Baz.java");
    rebuildProject();
    CompilerReferenceService.CompilerDirectInheritorInfo<PsiClass> directInheritorInfo = getHierarchyUnderForElementCaret();

    Collection<PsiClass> inheritors = directInheritorInfo.getDirectInheritors().collect(Collectors.toList());
    assertSize(4, inheritors);
    for (PsiClass inheritor : inheritors) {
      if (inheritor instanceof PsiAnonymousClass) {
        assertOneOf(inheritor.getTextOffset(), 58, 42, 94);
      } else {
        assertOneOf(inheritor.getQualifiedName(), "FooImpl", "FooImpl2");
      }
    }

    Collection<PsiClass> candidates = directInheritorInfo.getDirectInheritorCandidates().collect(Collectors.toList());
    assertEmpty(candidates);
  }

  private CompilerReferenceService.CompilerDirectInheritorInfo<PsiClass> getHierarchyUnderForElementCaret() {
    final PsiElement atCaret = myFixture.getElementAtCaret();
    assertNotNull(atCaret);
    final PsiClass classAtCaret = PsiTreeUtil.getParentOfType(atCaret, PsiClass.class, false);
    assertNotNull(classAtCaret);
    return CompilerReferenceService.getInstance(myFixture.getProject()).getDirectInheritors(classAtCaret,
                                                                                            assertInstanceOf(classAtCaret.getUseScope(), GlobalSearchScope.class),
                                                                                            assertInstanceOf(classAtCaret.getUseScope(), GlobalSearchScope.class),
                                                                                            JavaBaseCompilerSearchAdapter.INSTANCE,
                                                                                            StdFileTypes.JAVA);

  }

  private Set<VirtualFile> getReferentFilesForElementUnderCaret(CompilerSearchAdapter adapter) {
    final PsiElement atCaret = myFixture.getElementAtCaret();
    assertNotNull(atCaret);
    final PsiMember memberAtCaret = PsiTreeUtil.getParentOfType(atCaret, PsiMember.class, false);
    assertNotNull(memberAtCaret);
    return ((CompilerReferenceServiceImpl)CompilerReferenceService.getInstance(myFixture.getProject())).getReferentFiles(memberAtCaret, adapter);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }
}
