package com.intellij.compiler;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;

import static com.intellij.util.io.TestFileSystemBuilder.fs;

/**
 * @author nik
 */
public class ForcedCompilationTest extends BaseCompilerTestCase {
  @Override
  protected boolean useExternalCompiler() {
    return true;
  }

  public void testHandleDeletedFilesOnForcedCompilation() {
    VirtualFile a = createFile("dep/src/A.java", "class A{}");
    Module dep = addModule("dep", a.getParent());
    VirtualFile b = createFile("m/src/B.java", "class B {A a;}");
    Module m = addModule("m", b.getParent());
    ModuleRootModificationUtil.addDependency(m, dep);
    make(dep, m);
    assertOutput(dep, fs().file("A.class"));

    deleteFile(a);
    BuildManager.getInstance().clearState(myProject);
    recompile(dep);
    assertOutput(dep, fs());
    assertOutput(m, fs().file("B.class"));

    compile(getCompilerManager().createProjectCompileScope(myProject), CompilerFilter.ALL, false, true);
    assertOutput(dep, fs());
    assertOutput(m, fs());
  }
}
