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
package com.intellij.compiler.impl.rmiCompiler;

import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@State(
  name = "RmicSettings",
  storages = {
    @Storage( file = "$PROJECT_FILE$")
   ,@Storage( file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class RmicSettings extends JavacSettings {
  public boolean IS_EANABLED = false;
  public boolean GENERATE_IIOP_STUBS = false;

  @NonNls
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String[] getOptions() {
    List<String> options = new ArrayList<String>();
    if(DEBUGGING_INFO) {
      options.add("-g");
    }
    if(GENERATE_NO_WARNINGS) {
      options.add("-nowarn");
    }
    if(GENERATE_IIOP_STUBS) {
      options.add("-iiop");
    }
    final StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if("-g".equals(token)) {
        continue;
      }
      if("-iiop".equals(token)) {
        continue;
      }
      if("-nowarn".equals(token)) {
        continue;
      }
      options.add(token);
    }
    return ArrayUtil.toStringArray(options);
  }
}