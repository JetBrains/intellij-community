/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

import com.intellij.compiler.impl.CompileContextImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 18, 2006
 */
public class CompilationClasspathTest extends CompilerTestCase{
  private VirtualFile myTestsOutput;

  public CompilationClasspathTest() {
    super("common");
  }

  public void testClasspathWithOnlyProductionSet() {
    doSetup(false, true, false);

    final CompileContextImpl context = new CompileContextImpl(getProject(), null, null, null, false);
    final Set<VirtualFile> files = context.getTestOutputDirectories();
    assertTrue(files.isEmpty());
  }

  public void testClasspathWithOnlyTestsSet() throws IOException {
    doSetup(true, false, true);
    final CompileContextImpl context = new CompileContextImpl(getProject(), null, null, null, false);
    final Set<VirtualFile> files = context.getTestOutputDirectories();
    
    assertTrue(files.size() == 1);
    assertEquals(myClassesDir, files.iterator().next());
  }

  public void testClasspathProductionAndTestsDiffer() throws IOException {
    doSetup(true, true, false);
    final CompileContextImpl context = new CompileContextImpl(getProject(), null, null, null, false);
    final Set<VirtualFile> files = context.getTestOutputDirectories();
    
    assertTrue(files.size() == 1);
    assertTrue(myTestsOutput != null);
    assertEquals(myTestsOutput, files.iterator().next());
  }
  
  public void testClasspathProductionAndTestsAreSame() throws IOException {
    doSetup(true, false, false);
    final CompileContextImpl context = new CompileContextImpl(getProject(), null, null, null, false);
    final Set<VirtualFile> files = context.getTestOutputDirectories();
    
    assertEquals(myClassesDir, myTestsOutput);
    assertTrue(files.isEmpty());
  }
  
  
  private void doSetup(final boolean setupTestOutput, final boolean differentOutputRoots, final boolean removeProductionOutputRoot) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        //long start = System.currentTimeMillis();
        try {
          setup("anonymous"); // reuse existing test data
          if (setupTestOutput) {
            setupTestsOutput(differentOutputRoots, removeProductionOutputRoot);
          }
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }, ModalityState.NON_MODAL);
  }

  private void setupTestsOutput(final boolean different, final boolean removeProductionOutput){
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
          if (different) {
            myTestsOutput = myModuleRoot.createChildDirectory(this, "test_" + CLASSES);
          }
          else {
            myTestsOutput = myClassesDir;
          }
          final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
          compilerModuleExtension.setCompilerOutputPathForTests(myTestsOutput);
          if (removeProductionOutput) {
            compilerModuleExtension.setCompilerOutputPath((String)null);
          }
          rootModel.commit();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
  }
}
