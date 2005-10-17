/*
 * @author: Eugene Zhuravlev
 * Date: Oct 30, 2002
 * Time: 4:56:35 PM
 */
package com.intellij.compiler;

import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class CompilePackageTest extends CompilerTestCase{

  public CompilePackageTest() {
    super("compilePackage");
  }

  public void testSynchronizeOutput() throws Exception {doTest();}


  protected void doCompile(final CompileStatusNotification notification, int pass) {
    final CompilerManager compileManager = CompilerManager.getInstance(myProject);
    if (pass == 1) {
      compileManager.rebuild(/*null, null, */notification);
    }
    else if (pass == 2){
      final VirtualFile packageA = mySourceDir.findChild("a");
      assertTrue(packageA != null);

      final VirtualFile classB = mySourceDir.findFileByRelativePath("ab/B.java");
      final String path = classB.getPath();
      final boolean deleted = FileUtil.delete(new File(path));
      assertTrue("Cannot delete "+path , deleted);

      CompilerManagerImpl.clearPathsToCompile();

      compileManager.compile(new VirtualFile[] {packageA}, /*packageA, null, null,*/ notification, false);
    }
  }

  protected String[] getCompiledPathsToCheck() {
    return CompilerManagerImpl.getPathsToCompile();
  }
}
