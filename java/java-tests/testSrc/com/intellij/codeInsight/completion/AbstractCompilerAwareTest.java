/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public abstract class AbstractCompilerAwareTest extends JavaCodeInsightFixtureTestCase {
  private CompilerTester myCompilerTester;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCompilerTester = new CompilerTester(true, myModule);
  }

  @Override
  protected void tearDown() throws Exception {
    myCompilerTester.tearDown();
    super.tearDown();
  }

  protected final void compileAndIndexData(final String... fileNames) {
    final VirtualFile[] filesToCompile =
      ContainerUtil.map2Array(ContainerUtil.list(fileNames), new VirtualFile[fileNames.length], new Function<String, VirtualFile>() {
        @Override
        public VirtualFile fun(final String fileName) {
          try {
            return myFixture.addFileToProject(fileName, FileUtil.loadFile(new File(getTestDataPath() + getName() + "/" + fileName)))
              .getVirtualFile();
          }
          catch (final IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    for (final CompilerMessage compilerMessage : myCompilerTester.rebuild()) {
      assertNotSame(CompilerMessageCategory.ERROR, compilerMessage.getCategory());
    }
  }
}
