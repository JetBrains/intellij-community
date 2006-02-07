/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;

import java.io.IOException;

public class ResourcesTest extends CompilerTestCase {
  public ResourcesTest() {
    super("common");
  }

  protected boolean shouldExcludeOutputFromProject() {
    return false;
  }

  protected VirtualFile createSourcesDir() throws IOException {
    if (mySourceDir == null) {
      return super.createSourcesDir();
    }
    return mySourceDir;
  }

  protected VirtualFile createOutputDir() throws IOException {
    // let compiler output directly to sources
    return createSourcesDir();
  }


  protected void createTestProjectStructure(VirtualFile moduleRoot) throws Exception {
    super.createTestProjectStructure(moduleRoot);
    final CompilerConfiguration configuration = CompilerConfiguration.getInstance(myProject);
    copyTestProjectFiles(new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        return (file.isDirectory() || configuration.isResourceFile(file.getName())) && !"data.xml".equalsIgnoreCase(file.getName());
      }
    });
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myOriginalSourceDir.refresh(false, true);
      }
    });
  }

  protected void doCompile(final CompileStatusNotification notification, int pass) {
    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(myProject);
    final boolean shouldClear = config.CLEAR_OUTPUT_DIRECTORY;
    try {
      config.CLEAR_OUTPUT_DIRECTORY = false;
      CompilerManager.getInstance(myProject).rebuild(notification);
    }
    finally {
      config.CLEAR_OUTPUT_DIRECTORY = shouldClear;
    }
  }

  public void testNoResourceDelete() throws Exception  {doTest();}
}
