/*
 * @author: Eugene Zhuravlev
 * Date: Oct 30, 2002
 * Time: 4:56:35 PM
 */
package com.intellij.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class ChangeNameTest extends CompilerTestCase{

  public ChangeNameTest() {
    super("changeName");
  }

  public void testChangeClassName() throws Exception {doTest();}

  protected void doCompile(final CompileStatusNotification notification, int pass) {
    final CompilerManager compileManager = CompilerManager.getInstance(myProject);
    if (pass == 1) {
      compileManager.rebuild(/*null, null, notification*/notification);
    }
    else if (pass == 2){
      final VirtualFile classA = mySourceDir.findChild("A.java");
      final String path = classA.getPath();
      final boolean deleted = FileUtil.delete(new File(path));
      assertTrue("Cannot delete "+path , deleted);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          mySourceDir.refresh(false, true);
        }
      });

      compileManager.make(/*null, null, notification*/notification);
    }
  }
}
