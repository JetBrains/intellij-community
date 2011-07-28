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

import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import org.jetbrains.annotations.NonNls;

import java.util.StringTokenizer;

@State(
  name = "JikesSettings",
  storages = {
    @Storage( file = "$PROJECT_FILE$")
   ,@Storage( file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class JikesSettings extends JavacSettings {
  public String JIKES_PATH = "";
  public boolean IS_EMACS_ERRORS_MODE = true;

  @NonNls
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getOptionsString() {
    StringBuffer options = new StringBuffer();
    if(DEBUGGING_INFO) {
      options.append("-g ");
    }
    if(DEPRECATION) {
      options.append("-deprecation ");
    }
    if(GENERATE_NO_WARNINGS) {
      options.append("-nowarn ");
    }
    /*
    if(IS_INCREMENTAL_MODE) {
      options.append("++ ");
    }
    */
    if(IS_EMACS_ERRORS_MODE) {
      options.append("+E ");
    }

    StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if("-g".equals(token)) {
        continue;
      }
      if("-deprecation".equals(token)) {
        continue;
      }
      if("-nowarn".equals(token)) {
        continue;
      }
      if("++".equals(token)) {
        continue;
      }
      if("+M".equals(token)) {
        continue;
      }
      if("+F".equals(token)) {
        continue;
      }
      if("+E".equals(token)) {
        continue;
      }
      options.append(token);
      options.append(" ");
    }
    return options.toString();
  }
}