package com.intellij.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.intellij.util.io.TestFileSystemItem.fs;

/**
 * @author nik
 */
public class JavaCompilerBasicTest extends BaseCompilerTestCase {

  public void testAddRemoveJavaClass() throws IOException {
    final VirtualFile file = createFile("src/A.java", "public class A {}");
    VirtualFile srcRoot = file.getParent();
    final Module module = addModule("a", srcRoot);
    make(module);
    assertOutput(module, fs().file("A.class"));

    //perform second make to clear BuildManager.ProjectData.myNeedRescan which was set by 'rootsChanged' event caused by creation of output directory
    make(module);
    assertOutput(module, fs().file("A.class"));

    File b = new File(VfsUtil.virtualToIoFile(srcRoot), "B.java");
    FileUtil.writeToFile(b, "public class B{}");
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singletonList(b));
    make(module);
    assertOutput(module, fs().file("A.class").file("B.class"));

    deleteFile(file);
    make(module);
    assertOutput(module, fs().file("B.class"));
  }
}
