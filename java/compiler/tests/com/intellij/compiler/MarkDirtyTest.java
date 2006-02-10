/*
 * @author: Eugene Zhuravlev
 * Date: Oct 30, 2002
 * Time: 4:56:35 PM
 */
package com.intellij.compiler;

import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.vfs.VirtualFile;

public class MarkDirtyTest extends CompilerTestCase{

  public MarkDirtyTest() {
    super("markDirty");
  }

  public void testRecompileDependent() throws Exception {doTest();}

  protected void doCompile(final CompileStatusNotification notification, int pass) {
    final CompilerManager compileManager = CompilerManager.getInstance(myProject);
    if (pass == 1) {
      compileManager.rebuild(/*null, null, */notification);
    }
    else if (pass == 2){
      final VirtualFile serverSource = mySourceDir.findChild("Server.java");
      final VirtualFile clientSource = mySourceDir.findChild("Client.java");
      assertTrue(serverSource != null && clientSource != null);
      compileManager.compile(new VirtualFile[] {serverSource}, /*serverSource, null, null,*/ new CompileStatusNotification() {
        public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
          notification.finished(aborted, errors, warnings, compileContext);
          assertTrue("The files should be compiled without errors!", errors == 0 && !aborted);
          //CompileTimeInfo info = new CompileTimeInfo(CompilerUtil.getCompileTimeInfoStorePath(myProject));
          //assertTrue("Server class should not be marked as dirty", !info.isFileDirty(serverSource));
          //assertTrue("Client class should be marked as dirty", info.isFileDirty(clientSource));
        }
      }, false);
    }
  }
}
