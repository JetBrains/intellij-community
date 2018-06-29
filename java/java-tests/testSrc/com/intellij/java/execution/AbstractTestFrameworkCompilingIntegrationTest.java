// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.CompilerTester;

import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractTestFrameworkCompilingIntegrationTest extends AbstractTestFrameworkIntegrationTest {
  private CompilerTester myCompilerTester;
  
  protected abstract String getTestContentRoot();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setupModule();
    myCompilerTester = new CompilerTester(myModule);
    List<CompilerMessage> compilerMessages = myCompilerTester.rebuild();
    assertEmpty(compilerMessages.stream()
                  .filter(message -> message.getCategory() == CompilerMessageCategory.ERROR)
                  .collect(Collectors.toSet()));
  }

  protected void setupModule() throws Exception { 
    ModuleRootModificationUtil.updateModel(myModule, 
                                           model -> model.addContentEntry(getTestContentRoot())
                                             .addSourceFolder(getTestContentRoot() + "/test", true));

  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myCompilerTester.tearDown();
    }
    finally {
      super.tearDown();
    }
  }
}
