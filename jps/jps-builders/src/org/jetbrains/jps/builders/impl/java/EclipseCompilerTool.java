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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import javax.tools.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * @author nik
 */
public class EclipseCompilerTool extends JavaCompilingTool {
  @NotNull
  @Override
  public String getId() {
    return JavaCompilers.ECLIPSE_ID;
  }

  @Nullable
  @Override
  public String getAlternativeId() {
    return JavaCompilers.ECLIPSE_EMBEDDED_ID;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Eclipse compiler";
  }

  @NotNull
  @Override
  public JavaCompiler createCompiler() throws CannotCreateJavaCompilerException {
    for (JavaCompiler javaCompiler : ServiceLoader.load(JavaCompiler.class)) {
      if ("EclipseCompiler".equals(StringUtil.getShortName(javaCompiler.getClass()))) {
        return javaCompiler;
      }
    }
    throw new CannotCreateJavaCompilerException("Eclipse Batch Compiler was not found in classpath");
  }

  @NotNull
  @Override
  public List<File> getAdditionalClasspath() {
    for (JavaCompiler javaCompiler : ServiceLoader.load(JavaCompiler.class)) { // Eclipse compiler
      final File compilerResource = ClasspathBootstrap.getResourceFile(javaCompiler.getClass());
      final String name = compilerResource.getName();
      if (name.startsWith("ecj-") && name.endsWith(".jar")) {
        return Collections.singletonList(compilerResource);
      }
    }
    return Collections.emptyList();
  }

  @Override
  public void processCompilerOptions(@NotNull CompileContext context, @NotNull List<String> options) {
    for (String option : options) {
      if (option.startsWith("-proceedOnError")) {
        Utils.PROCEED_ON_ERROR_KEY.set(context, Boolean.TRUE);
        break;
      }
    }
  }

  @Override
  public List<String> getDefaultCompilerOptions() {
    return Collections.singletonList("-noExit");
  }
}
