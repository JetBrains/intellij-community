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
package com.intellij.compiler.impl.javaCompiler.jikes;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.compiler.ParserAction;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.FileObject;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * This class provides compatibility with older javac(english locale) and jikes versions and
 * implements parsing method from previous IDEA versions for jikes and javac compiler output.
 * Used in order not to miss some compiler output messages when jikes is configured as a preferred compiler
 * Also used in test mode in which javac is not run via JavacRunner
 *
 * @author Eugene Zhuravlev
 *         Date: Sep 24, 2005
 */
public class ParserActionJikes extends ParserAction {
  @NonNls private static final String JAVA_EXTENSION = ".java";

  public boolean execute(@NonNls String line, final OutputParser.Callback callback) {
    if (!StringUtil.startsWithChar(line, '[') || !StringUtil.endsWithChar(line, ']')) {
      return false;
    }
    if (line.startsWith("[parsing started")){ // javac
      String filePath = line.substring("[parsing started".length(), line.length() - 1).trim();
      processParsingMessage(callback, filePath.replace(File.separatorChar, '/'));
    }
    else if (line.startsWith("[parsed") && line.contains(JAVA_EXTENSION)) { // javac version 1.2.2
      int index = line.indexOf(JAVA_EXTENSION);
      String filePath = line.substring("[parsed".length(), index + JAVA_EXTENSION.length()).trim();
      processParsingMessage(callback, filePath.replace(File.separatorChar, '/'));
    }
    else if (line.startsWith("[read") && line.endsWith(".java]")){ // jikes
      String filePath = line.substring("[read".length(), line.length() - 1).trim();
      processParsingMessage(callback, filePath.replace(File.separatorChar, '/'));
    }
    else if (line.startsWith("[parsing completed")){
    }
    else if (line.startsWith("[loading") || line.startsWith("[loaded") || line.startsWith("[read")){
      callback.setProgressText(CompilerBundle.message("progress.loading.classes"));
    }
    else if (line.startsWith("[checking")){
      String className = line.substring("[checking".length(), line.length() - 1).trim();
      callback.setProgressText(CompilerBundle.message("progress.compiling.class", className));
    }
    else if (line.startsWith("[wrote") || line.startsWith("[write")){
      String filePath = line.substring("[wrote".length(), line.length() - 1).trim();
      processParsingMessage(callback, filePath.replace(File.separatorChar, '/'));
    }
    return true;
  }

  private static void processParsingMessage(final OutputParser.Callback callback, @NonNls final String filePath) {
    int index = filePath.lastIndexOf('/');
    final String name = index >= 0 ? filePath.substring(index + 1) : filePath;

    final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    if (StdFileTypes.JAVA.equals(fileType)) {
      callback.fileProcessed(filePath);
      callback.setProgressText(CompilerBundle.message("progress.parsing.file", name));
    }
    else if (StdFileTypes.CLASS.equals(fileType)) {
      callback.fileGenerated(new FileObject(new File(filePath)));
    }
  }
}
