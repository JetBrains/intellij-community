// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Dmitry Batkovich
 */
public abstract class AbstractCompilerAwareTest extends JavaCodeInsightFixtureTestCase {
  private CompilerTester myCompilerTester;

  @Override
  protected void tearDown() throws Exception {
    myCompilerTester = null;
    super.tearDown();
  }

  protected final void installCompiler() {
    try {
      myCompilerTester = new CompilerTester(myFixture, Arrays.asList(ModuleManager.getInstance(getProject()).getModules()));
    }
    catch (Exception e) {
      fail(e.getMessage());
    }
  }

  protected final void compileAndIndexData(final String... fileNames) {
    try {
      for (String fileName : fileNames) {
        myFixture.addFileToProject(fileName, FileUtil.loadFile(new File(getTestDataPath() + getName() + "/" + fileName))).getVirtualFile();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    rebuildProject();
  }

  protected void rebuildProject() {
    for (final CompilerMessage compilerMessage : myCompilerTester.rebuild()) {
      assertNotSame(compilerMessage.getMessage(), CompilerMessageCategory.ERROR, compilerMessage.getCategory());
    }
  }
}
