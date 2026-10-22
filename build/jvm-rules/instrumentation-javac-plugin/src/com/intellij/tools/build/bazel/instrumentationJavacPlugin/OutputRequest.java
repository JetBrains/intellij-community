// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.sun.source.tree.CompilationUnitTree;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The output request that javac's ClassWriter makes for one class: the output location, the class name and the source
 * file as the sibling. A new file object for the same request names the class file that javac wrote.
 */
final class OutputRequest {
  private final Location myLocation;
  private final String myClassName;
  private final JavaFileObject mySibling;
  private final CompilationUnitTree myUnit;

  /**
   * @param unit the compilation unit of the class, for the diagnostics
   */
  OutputRequest(Location location, String className, JavaFileObject sibling, CompilationUnitTree unit) {
    myLocation = location;
    myClassName = className;
    mySibling = sibling;
    myUnit = unit;
  }

  String className() {
    return myClassName;
  }

  CompilationUnitTree unit() {
    return myUnit;
  }

  String internalName() {
    return myClassName.replace('.', '/');
  }

  byte[] read(JavaFileManager fileManager) throws IOException {
    try (InputStream in = outputFile(fileManager).openInputStream()) {
      return in.readAllBytes();
    }
  }

  void write(JavaFileManager fileManager, byte[] content) throws IOException {
    try (OutputStream out = outputFile(fileManager).openOutputStream()) {
      out.write(content);
    }
  }

  private JavaFileObject outputFile(JavaFileManager fileManager) throws IOException {
    return fileManager.getJavaFileForOutput(myLocation, myClassName, JavaFileObject.Kind.CLASS, mySibling);
  }
}
