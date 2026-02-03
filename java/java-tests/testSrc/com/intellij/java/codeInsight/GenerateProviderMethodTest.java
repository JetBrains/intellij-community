// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.generation.GenerateProviderMethodHandler;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;

@NeedsIndex.SmartMode(reason = "Provider method completion is not supported in the dumb mode")
public class GenerateProviderMethodTest extends LightFixtureCompletionTestCase {

  private MultiModuleProjectDescriptor myDescriptor;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor == null
           ? myDescriptor =
             new MultiModuleProjectDescriptor(Paths.get(getTestDataPath()), "providers", null)
           : myDescriptor;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/generateProviderMethod";
  }

  public void testNonModuleClass() {
    doTest("src/org/jetbrains/providers/SimpleClass.java");
  }

  public void testSimpleProvider() {
    doTest("src/org/jetbrains/providers/MyProviderImpl.java");
  }

  public void testWithProvider() {
    doTest("src/org/jetbrains/providers/WithProvider.java");
  }

  public void testSubClass() {
    doTest("src/org/jetbrains/providers/MySuperClass.java");
  }

  public void testWrongPlace() {
    doTest("src/org/jetbrains/providers/WrongPlace.java");
  }

  private void doTest(@NotNull String path) {
    VirtualFile file = getModule().getModuleFile().getParent().findFileByRelativePath(path);
    myFixture.configureFromExistingVirtualFile(file);

    new GenerateProviderMethodHandler().invoke(getProject(), getEditor(), PsiManager.getInstance(getProject()).findFile(file));

    checkResultByFile("after/" + path);
  }
}