/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.CompilerEncodingService;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.openapi.module.Module;
import com.intellij.util.Chunk;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

public class JavacSettingsBuilder {
  private final JpsJavaCompilerOptions myOptions;

  public JavacSettingsBuilder(JpsJavaCompilerOptions options) {
    myOptions = options;
  }

  protected JpsJavaCompilerOptions getOptions() {
    return myOptions;
  }

  public Collection<String> getOptions(Chunk<Module> chunk) {
    List<String> options = new ArrayList<String>();
    if (getOptions().DEBUGGING_INFO) {
      options.add("-g");
    }
    if (getOptions().DEPRECATION) {
      options.add("-deprecation");
    }
    if (getOptions().GENERATE_NO_WARNINGS) {
      options.add("-nowarn");
    }
    boolean isEncodingSet = false;
    final StringTokenizer tokenizer = new StringTokenizer(getOptions().ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();
      if(!acceptUserOption(token)) {
        continue;
      }
      options.add(token);
      if ("-encoding".equals(token)) {
        isEncodingSet = true;
      }
    }
    if (!isEncodingSet && acceptEncoding()) {
      final Charset charset = CompilerEncodingService.getPreferredModuleEncoding(chunk);
      if (charset != null) {
        options.add("-encoding");
        options.add(charset.name());
      }
    }
    return options;
  }

  protected boolean acceptUserOption(String token) {
    return !("-g".equals(token) || "-deprecation".equals(token) || "-nowarn".equals(token));
  }

  protected boolean acceptEncoding() {
    return true;
  }

  public String getOptionsString(final ModuleChunk chunk) {
    final StringBuilder options = new StringBuilder();
    for (String option : getOptions(chunk)) {
      if (options.length() > 0) {
        options.append(" ");
      }
      options.append(option);
    }
   return options.toString();
  }
}