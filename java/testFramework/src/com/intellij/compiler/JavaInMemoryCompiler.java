// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import org.intellij.lang.annotations.Language;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author Bas Leijdekkers
 */
public class JavaInMemoryCompiler {

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private final JavaMemFileManager myFileManager = new JavaMemFileManager();
  private final JavaCompiler myCompiler = ToolProvider.getSystemJavaCompiler();

  public JavaInMemoryCompiler(File... classpath) {
    try {
      myFileManager.setLocation(StandardLocation.CLASS_PATH, Arrays.asList(classpath));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, byte[]> compile(String className, @Language("JAVA") String code) {
    final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    final Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(new JavaSourceFromString(className, code));
    final Iterable<String> options = Collections.singletonList("-g"); // generate debugging info.
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    final OutputStreamWriter out = new OutputStreamWriter(System.err, StandardCharsets.UTF_8);
    final Boolean success = myCompiler.getTask(out, myFileManager, diagnostics, options, null, compilationUnits).call();

    if (!success.booleanValue()) {
      final StringWriter writer = new StringWriter();
      for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
        writer.write(diagnostic.getMessage(Locale.ENGLISH));
        writer.write(" on line " + diagnostic.getLineNumber());
        writer.write("\n");
      }
      throw new RuntimeException(writer.toString());
    }
    final List<InMemoryClassFile> classFiles = myFileManager.getClassFiles();
    final Map<String, byte[]> result = new HashMap<>(2);
    for (InMemoryClassFile classFile : classFiles) {
      result.put(classFile.getClassName(), classFile.getBytes());
    }
    return result;
  }

  public static class JavaSourceFromString extends SimpleJavaFileObject {

    final String myCode;

    JavaSourceFromString(String className, String code) {
      super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.myCode = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return myCode;
    }
  }

  public static class InMemoryClassFile extends SimpleJavaFileObject {

    private final String myClassName;
    ByteArrayOutputStream myOutputStream = new ByteArrayOutputStream();

    public InMemoryClassFile(String className) {
      super(URI.create("class:///" + className.replace('.', '/') + ".class"), Kind.CLASS);
      myClassName = className;
    }

    public String getClassName() {
      return myClassName;
    }

    public byte[] getBytes() {
      return myOutputStream.toByteArray();
    }

    @Override
    public OutputStream openOutputStream() {
      myOutputStream.reset();
      return myOutputStream;
    }
  }

  public static class JavaMemFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

    private final List<InMemoryClassFile> myClassFiles = new ArrayList<>(2);

    public JavaMemFileManager() {
      super(ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null));
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling)
      throws IOException {
      if (StandardLocation.CLASS_OUTPUT == location && JavaFileObject.Kind.CLASS == kind) {
        final InMemoryClassFile file = new InMemoryClassFile(className);
        myClassFiles.add(file);
        return file;
      }
      else {
        return super.getJavaFileForOutput(location, className, kind, sibling);
      }
    }

    public void setLocation(Location location, Iterable<? extends File> path) throws IOException {
      fileManager.setLocation(location, path);
    }

    public List<InMemoryClassFile> getClassFiles() {
      return myClassFiles;
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
      return null;
    }
  }
}