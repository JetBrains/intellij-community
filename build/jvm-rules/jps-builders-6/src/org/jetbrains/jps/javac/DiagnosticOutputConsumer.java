// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.jps.javac.ast.api.JavacFileData;

import javax.tools.*;
import java.io.File;

public interface DiagnosticOutputConsumer extends DiagnosticListener<JavaFileObject> {

  void outputLineAvailable(String line);

  void registerJavacFileData(JavacFileData data);

  void javaFileLoaded(File file);

  void customOutputData(String pluginId, String dataName, byte[] data);
}
