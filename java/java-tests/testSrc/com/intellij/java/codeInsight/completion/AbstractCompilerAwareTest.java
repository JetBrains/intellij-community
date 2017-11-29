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
package com.intellij.java.codeInsight.completion;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public abstract class AbstractCompilerAwareTest extends JavaCodeInsightFixtureTestCase {
  private CompilerTester myCompilerTester;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void installCompiler() {
    try {
      myCompilerTester = new CompilerTester(getProject(), ContainerUtil.list(ModuleManager.getInstance(getProject()).getModules()));
    }
    catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myCompilerTester != null) {
        myCompilerTester.tearDown();
      }
    }
    finally {
      myCompilerTester = null;
      super.tearDown();
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
