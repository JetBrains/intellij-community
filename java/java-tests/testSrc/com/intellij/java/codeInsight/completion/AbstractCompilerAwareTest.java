// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public abstract class AbstractCompilerAwareTest extends JavaCodeInsightFixtureTestCase {
  private CompilerTester myCompilerTester;

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_11);
  }

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

  protected final void compileAndIndexData(String... fileNames) throws IOException {
    for (String fileName : fileNames) {
      myFixture.addFileToProject(fileName, FileUtil.loadFile(new File(getTestDataPath() + getName() + "/" + fileName))).getVirtualFile();
    }
    rebuildProject();
  }

  protected void rebuildProject() {
    for (final CompilerMessage compilerMessage : myCompilerTester.rebuild()) {
      assertNotSame("File: " + compilerMessage.getVirtualFile() + ", " + compilerMessage.getMessage(),
                    CompilerMessageCategory.ERROR, compilerMessage.getCategory());
    }
  }
}
