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
import com.intellij.find.bytecode.CompilerReferenceService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.containers.ContainerUtil;

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

  private Set<VirtualFile> getReferentFilesForElementUnderCaret() {
    final PsiElement atCaret = myFixture.getElementAtCaret();
    assertNotNull(atCaret);
    final PsiMember memberAtCaret = PsiTreeUtil.getParentOfType(atCaret, PsiMember.class, false);
    assertNotNull(memberAtCaret);
    return ((CompilerReferenceServiceImpl)CompilerReferenceService.getInstance(myFixture.getProject())).getReferentFiles(memberAtCaret);
  }
}
