// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.javac.JavacMain;

import javax.tools.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

public final class JavacCompilerTool extends JavaCompilingTool {
  public static final String ID = "Javac"; // duplicates org.jetbrains.jps.model.java.compiler.JavaCompilers.JAVAC_ID;
  public static final String ALTERNATIVE_ID = "compAPI"; // duplicates org.jetbrains.jps.model.java.compiler.JavaCompilers.JAVAC_API_ID;

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nullable
  @Override
  public String getAlternativeId() {
    return ALTERNATIVE_ID;
  }

  @Override
  public boolean isCompilerTreeAPISupported() {
    return true;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "javac " + System.getProperty("java.version");
  }

  @NotNull
  @Override
  public JavaCompiler createCompiler() throws CannotCreateJavaCompilerException {
    Throwable err1 = null;
    try {
      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      if (compiler != null) {
        return compiler;
      }
    }
    catch (Throwable ex) {
      err1 = ex;
    }

    String message;
    // trying to obtain additional diagnostic for the case when compiler.jar is present, but there were problems with compiler class loading:
    try {
      //temporary workaround for IDEA-169747: try to create the instance by hand if it was found
      return (JavaCompiler)Class.forName("com.sun.tools.javac.api.JavacTool", true, JavacMain.class.getClassLoader()).newInstance();
    }
    catch (Throwable ex) {
      message = (err1 != null ? formatErrorMessage("Error obtaining system java compiler", err1) + "\n" : "") + formatErrorMessage("System Java Compiler was not found in classpath", ex);
    }

    throw new CannotCreateJavaCompilerException(message);
  }

  @NotNull
  private static String formatErrorMessage(final String header, Throwable ex) {
    StringWriter stringWriter = new StringWriter();
    stringWriter.write(header);
    stringWriter.write(":\n");
    ex.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.getBuffer().toString();
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
