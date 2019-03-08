package com.intellij.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.Compressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.javac.JpsJavacFileManager;
import org.jetbrains.jps.javac.OutputFileObject;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;

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

    File b = new File(VfsUtilCore.virtualToIoFile(srcRoot), "B.java");
    FileUtil.writeToFile(b, "public class B{}");
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singletonList(b));
    make(module);
    assertOutput(module, fs().file("A.class").file("B.class"));

    deleteFile(file);
    make(module);
    assertOutput(module, fs().file("B.class"));
  }

  public void testFilterSourcesFromLibraries() throws IOException {
    final VirtualFile libJavaFile = createFile("lib/ppp/B.java", "package ppp; public class B {}");
    final VirtualFile libClsFile = createFile("lib/ppp/B.class", "package ppp; public class B {}");
    final File jarFile = new File(libJavaFile.getParent().getParent().getPath(), "lib.jar");
    try (Compressor.Jar jar = new Compressor.Jar(jarFile)) {
      jar.addFile("ppp/B.java", new File(libJavaFile.getPath()));
      jar.addFile("ppp/B.class", new File(libClsFile.getPath()));
    }
    final VirtualFile srcFile = createFile("src/A.java", "import ppp.B; public class A { B b; }");

    final StandardJavaFileManager stdFileManager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(new DiagnosticListener<JavaFileObject>() {
      @Override
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      }
    }, Locale.US, null);
    final JpsJavacFileManager fileManager = new JpsJavacFileManager(new JpsJavacFileManager.Context() {
      @Override
      public boolean isCanceled() {
        return false;
      }

      @NotNull
      @Override
      public StandardJavaFileManager getStandardFileManager() {
        return stdFileManager;
      }

      @Override
      public void consumeOutputFile(@NotNull OutputFileObject obj) {
      }

      @Override
      public void reportMessage(Diagnostic.Kind kind, String message) {

      }
    }, true, Collections.emptyList());

    fileManager.setLocation(StandardLocation.CLASS_PATH, Collections.singleton(jarFile));
    fileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.emptyList());

    final File src = new File(srcFile.getPath());
    final Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(Collections.singleton(src));
    final Iterator<? extends JavaFileObject> it = sources.iterator();
    assertTrue(it.hasNext());
    final JavaFileObject srcFileObject = it.next();
    assertFalse(it.hasNext());
    assertEquals(JavaFileObject.Kind.SOURCE, srcFileObject.getKind());
    assertEquals(src.toURI().getPath(), srcFileObject.toUri().getPath());

    final Iterable<JavaFileObject> libClasses = fileManager.list(StandardLocation.CLASS_PATH, "ppp", Collections.singleton(JavaFileObject.Kind.CLASS), false);
    final Iterator<JavaFileObject> clsIterator = libClasses.iterator();
    assertTrue(clsIterator.hasNext());
    final JavaFileObject aClass = clsIterator.next();
    assertEquals(JavaFileObject.Kind.CLASS, aClass.getKind());
    assertEquals(jarFile.toURI().getPath() + "!/ppp/B.class", aClass.toUri().getPath());
    assertFalse(clsIterator.hasNext());
  }


  public void testSymlinksInSources() throws IOException {
    final VirtualFile file = createFile("src/A.java", "public class A {}");
    VirtualFile srcRoot = file.getParent();
    final File linkFile = new File(srcRoot.getParent().getPath(), "src-link");
    FileUtil.delete(linkFile); // ensure the link does not exist
    String symlink = Files.createSymbolicLink(linkFile.toPath(), Paths.get(srcRoot.getPath())).getFileName().toString();

    VirtualFile sourceRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(srcRoot.getParent().getPath(), symlink));
    assertNotNull(sourceRoot);
    final Module module = addModule("a", sourceRoot);

    make(module);
    assertOutput(module, fs().file("A.class"));
  }
}
