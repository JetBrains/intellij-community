// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.Compressor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.javac.JpsJavacFileManager;
import org.jetbrains.jps.javac.JpsJavacFileProvider;
import org.jetbrains.jps.javac.OutputFileObject;
import org.jetbrains.jps.javac.ZipFileObject;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.intellij.util.io.TestFileSystemItem.fs;

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

    final StandardJavaFileManager stdFileManager = getSystemJavaCompiler().getStandardFileManager(new DiagnosticListener<>() {
      @Override
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      }
    }, Locale.US, null);

    try (final JpsJavacFileManager fileManager = new JpsJavacFileManager(new DummyContext(stdFileManager), true, Collections.emptyList(), (JpsJavacFileProvider)null)) {
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
  }

  public void testFilterSourcesFromOutput() throws IOException {
    final VirtualFile javaFile = createFile("out/ppp/B.java", "package ppp; public class B {}");
    final VirtualFile clsFile = createFile("out/ppp/B.class", "package ppp; public class B {}");
    final File outputRoot = new File(javaFile.getParent().getParent().getPath());

    final StandardJavaFileManager stdFileManager = getSystemJavaCompiler().getStandardFileManager(new DiagnosticListener<>() {
      @Override
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      }
    }, Locale.US, null);

    try (final JpsJavacFileManager fileManager = new JpsJavacFileManager(new DummyContext(stdFileManager), true, Collections.emptyList(), (JpsJavacFileProvider)null)) {
      fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(outputRoot));

      final Iterable<JavaFileObject> files = fileManager.list(StandardLocation.CLASS_OUTPUT, "ppp", Set.of(JavaFileObject.Kind.CLASS, JavaFileObject.Kind.OTHER), false);
      final Iterator<JavaFileObject> resultIterator = files.iterator();
      assertTrue(resultIterator.hasNext());
      final JavaFileObject item = resultIterator.next();
      assertEquals(JavaFileObject.Kind.CLASS, item.getKind());
      assertEquals(new File(clsFile.getPath()), new File(item.toUri().getPath()));
      if (resultIterator.hasNext()) {
        StringBuilder msg = new StringBuilder();
        msg.append("These files should not be included in result:");
        while (resultIterator.hasNext()) {
          msg.append("\n").append(resultIterator.next().toUri().getPath());
        }
        fail(msg.toString());
      }
    }
  }

  public void testFileObjectComparison() throws IOException {
    final VirtualFile srcAFile = createFile("src/A.java", "public class A {}");
    final VirtualFile srcBFile = createFile("src/B.java", "public class B {}");
    final File jarFile = new File(srcAFile.getParent().getParent().getPath(), "lib.jar");
    try (Compressor.Jar jar = new Compressor.Jar(jarFile)) {
      jar.addFile("arch/A.java", new File(srcAFile.getPath()));
      jar.addFile("arch/B.java", new File(srcBFile.getPath()));
    }

    final StandardJavaFileManager stdFileManager = getSystemJavaCompiler().getStandardFileManager(new DiagnosticListener<>() {
      @Override
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      }
    }, Locale.US, null);
    
    try (final JpsJavacFileManager fileManager = new JpsJavacFileManager(new DummyContext(stdFileManager), true, Collections.emptyList(), (JpsJavacFileProvider)null)) {
      fileManager.setLocation(StandardLocation.CLASS_PATH, Collections.singleton(jarFile));
      fileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.emptyList());

      final File srcA = new File(srcAFile.getPath());
      final File srcB = new File(srcBFile.getPath());
      final Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(srcA, srcB));

      final Iterator<? extends JavaFileObject> it = sources.iterator();
      assertTrue(it.hasNext());
      final JavaFileObject srcAFileObject = it.next();
      assertTrue(it.hasNext());
      final JavaFileObject srcBFileObject = it.next();
      assertFalse(it.hasNext());

      assertEquals(JavaFileObject.Kind.SOURCE, srcAFileObject.getKind());
      assertEquals(JavaFileObject.Kind.SOURCE, srcBFileObject.getKind());
      assertEquals(srcA.toURI().getPath(), srcAFileObject.toUri().getPath());
      assertEquals(srcB.toURI().getPath(), srcBFileObject.toUri().getPath());
      assertTrue(fileManager.isSameFile(srcAFileObject, srcAFileObject));
      assertFalse(fileManager.isSameFile(srcAFileObject, srcBFileObject));
      checkFileObjectsBelongToLocation(fileManager, StandardLocation.SOURCE_PATH, sources);

      final Iterable<JavaFileObject> libClasses = fileManager.list(StandardLocation.CLASS_PATH, "arch", Collections.singleton(JavaFileObject.Kind.SOURCE), false);
      final Iterator<JavaFileObject> clsIterator = libClasses.iterator();
      assertTrue(clsIterator.hasNext());
      final JavaFileObject res1 = clsIterator.next();
      assertTrue(clsIterator.hasNext());
      final JavaFileObject res2 = clsIterator.next();
      assertFalse(clsIterator.hasNext());

      assertTrue(res1 instanceof ZipFileObject);
      assertEquals(JavaFileObject.Kind.SOURCE, res1.getKind());

      assertTrue(res2 instanceof ZipFileObject);
      assertEquals(JavaFileObject.Kind.SOURCE, res2.getKind());

      assertFalse(fileManager.isSameFile(res1, res2));
      checkFileObjectsBelongToLocation(fileManager, StandardLocation.CLASS_PATH, libClasses);
    }
  }

  public void testPatchModulePath() throws IOException {
    final VirtualFile srcAFile = createFile("src/A.java", "public class A {}");
    final VirtualFile srcBFile = createFile("src/B.java", "public class B {}");
    final File srcRoot = new File(srcAFile.getParent().getPath());

    final StandardJavaFileManager stdFileManager = getSystemJavaCompiler().getStandardFileManager(new DiagnosticListener<>() {
      @Override
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      }
    }, Locale.US, null);

    try (final JpsJavacFileManager fileManager = new JpsJavacFileManager(new DummyContext(stdFileManager), true, Collections.emptyList(), (JpsJavacFileProvider)null)) {
      fileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.emptyList());
      fileManager.handleOption("--patch-module", Arrays.asList("java.desktop=" + srcRoot.getPath()).iterator());

      final File srcA = new File(srcAFile.getPath());
      final File srcB = new File(srcBFile.getPath());
      final Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(srcA, srcB));

      final Iterator<? extends JavaFileObject> it = sources.iterator();
      assertTrue(it.hasNext());
      final JavaFileObject srcAFileObject = it.next();
      assertTrue(it.hasNext());
      final JavaFileObject srcBFileObject = it.next();
      assertFalse(it.hasNext());

      assertEquals(JavaFileObject.Kind.SOURCE, srcAFileObject.getKind());
      assertEquals(JavaFileObject.Kind.SOURCE, srcBFileObject.getKind());
      assertEquals(srcA.toURI().getPath(), srcAFileObject.toUri().getPath());
      assertEquals(srcB.toURI().getPath(), srcBFileObject.toUri().getPath());
      assertTrue(fileManager.isSameFile(srcAFileObject, srcAFileObject));
      assertFalse(fileManager.isSameFile(srcAFileObject, srcBFileObject));
      checkFileObjectsBelongToLocation(fileManager, StandardLocation.SOURCE_PATH, sources);
      checkFileObjectsBelongToLocation(fileManager, StandardLocation.PATCH_MODULE_PATH, sources);
    }
  }

  private static JavaCompiler getSystemJavaCompiler() throws IOException{
    try {
      return new JavacCompilerTool().createCompiler();
    }
    catch (CannotCreateJavaCompilerException e) {
      throw new IOException(e);
    }
  }

  private static void checkFileObjectsBelongToLocation(JpsJavacFileManager fileManager, final JavaFileManager.Location location, Iterable<? extends FileObject> fileObjects) throws IOException {
    for (FileObject source : fileObjects) {
      assertTrue(source.getName() + " should belong to " + location.getName(), fileManager.contains(location, source));
    }
  }

  public void testSymlinksInSources() throws IOException {
    if (!IoTestUtil.isSymLinkCreationSupported) {
      System.out.println("Test " + getTestName(false) + " skipped because symlink creation is not supported on this machine");
      return;
    }
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

  private static final class DummyContext implements JpsJavacFileManager.Context {
    private final StandardJavaFileManager myStdFileManager;

    DummyContext(StandardJavaFileManager stdFileManager) {
      myStdFileManager = stdFileManager;
    }

    @Nullable
    @Override
    public String getExplodedAutomaticModuleName(File pathElement) {
      return null;
    }

    @Override
    public boolean isCanceled() {
      return false;
    }

    @NotNull
    @Override
    public StandardJavaFileManager getStandardFileManager() {
      return myStdFileManager;
    }

    @Override
    public void consumeOutputFile(@NotNull OutputFileObject obj) {
    }

    @Override
    public void reportMessage(Diagnostic.Kind kind, @Nls String message) {
    }
  }
}
