/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.impl.java;

import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.javac.JavacMain;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import javax.tools.*;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JavacCompilerTool extends JavaCompilingTool {
  @NotNull
  @Override
  public String getId() {
    return JavaCompilers.JAVAC_ID;
  }

  @Nullable
  @Override
  public String getAlternativeId() {
    return JavaCompilers.JAVAC_API_ID;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "javac " + System.getProperty("java.version");
  }

  @NotNull
  @Override
  public JavaCompiler createCompiler() throws CannotCreateJavaCompilerException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler != null) {
      return compiler;
    }

    String message = "System Java Compiler was not found in classpath";
    // trying to obtain additional diagnostic for the case when compiler.jar is present, but there were problems with compiler class loading:
    try {
      Class.forName("com.sun.tools.javac.api.JavacTool", false, JavacMain.class.getClassLoader());
    }
    catch (Throwable ex) {
      message = message + ":\n" + ExceptionUtil.getThrowableText(ex);
    }
    throw new CannotCreateJavaCompilerException(message);
  }

  @NotNull
  @Override
  public List<File> getAdditionalClasspath() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getDefaultCompilerOptions() {
    return Collections.singletonList("-implicit:class");
  }
}
