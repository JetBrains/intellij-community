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
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.FileObject;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EclipseCompilerOutputParser extends OutputParser {
  private final String myOutputDir;

  public EclipseCompilerOutputParser(final String outputDir) {
    myOutputDir = outputDir;
  }

  @NonNls private static final Pattern PATH_PATTERN = Pattern.compile("\\s*(.*) - #.*");
  @NonNls private static final Pattern COMPILED_PATTERN = Pattern.compile("\\[\\d* unit(s)? compiled\\]");
  @NonNls private static final Pattern GENERATED_PATTERN = Pattern.compile("\\[\\d* \\.class file(s)? generated\\]");
  public boolean processMessageLine(Callback callback) {
    @NonNls String line = callback.getNextLine();
    if (line == null) {
      return false;
    }
    if (line.trim().length() == 0) {
      return true;
    }
    if (line.startsWith("[parsing ")) {
      Matcher matcher = PATH_PATTERN.matcher(line.substring("[parsing ".length()));
      matcher.matches();
      String path = matcher.group(1);
      callback.setProgressText(CompilerBundle.message("eclipse.compiler.parsing", path));
      callback.fileProcessed(path);
      return true;
    }
    if (line.startsWith("[reading ")) {
      //StringTokenizer tokenizer = new StringTokenizer(line.substring("[reading ".length()), " ]");
      //String fqn = tokenizer.nextToken();
      callback.setProgressText(CompilerBundle.message("eclipse.compiler.reading"));
      return true;
    }
    if (line.startsWith("[analyzing ")) {
      Matcher matcher = PATH_PATTERN.matcher(line.substring("[analyzing ".length()));
      matcher.matches();
      String path = matcher.group(1);
      callback.setProgressText(CompilerBundle.message("eclipse.compiler.analyzing", path));
      return true;
    }
    if (line.startsWith("[completed ")) {
      //Matcher matcher = PATH_PATTERN.matcher(line.substring("[completed ".length()));
      //matcher.matches();
      //String path = matcher.group(1);
      //callback.setProgressText(CompilerBundle.message("eclipse.compiler.completed", path));
      return true;
    }
    if (line.startsWith("[writing ")) {
      Matcher matcher = PATH_PATTERN.matcher(line.substring("[writing ".length()));
      matcher.matches();
      String path = matcher.group(1);
      String absPath = FileUtil.toSystemDependentName(myOutputDir + '/' + path);
      //callback.setProgressText(CompilerBundle.message("eclipse.compiler.writing", absPath));
      callback.fileGenerated(new FileObject(new File(absPath)));
      return true;
    }
    if (COMPILED_PATTERN.matcher(line).matches() || GENERATED_PATTERN.matcher(line).matches()) {
      return true;
    }
    callback.message(CompilerMessageCategory.INFORMATION, line, null, -1, -1);
    return true;
  }
}
