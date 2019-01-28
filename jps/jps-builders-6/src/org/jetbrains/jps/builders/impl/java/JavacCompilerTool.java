/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/**
 * @author nik
 */
public class JavacCompilerTool extends JavaCompilingTool {

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
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler != null) {
      return compiler;
    }

    String message;
    // trying to obtain additional diagnostic for the case when compiler.jar is present, but there were problems with compiler class loading:
    try {
      //temporary workaround for IDEA-169747: try to create the instance by hand if it was found
      return (JavaCompiler)Class.forName("com.sun.tools.javac.api.JavacTool", true, JavacMain.class.getClassLoader()).newInstance();
    }
    catch (Throwable ex) {
      StringWriter stringWriter = new StringWriter();
      stringWriter.write("System Java Compiler was not found in classpath");
      stringWriter.write(":\n");
      ex.printStackTrace(new PrintWriter(stringWriter));
      message = stringWriter.getBuffer().toString();
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
