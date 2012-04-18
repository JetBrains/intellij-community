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
import com.intellij.openapi.module.Module;
import com.intellij.util.Chunk;

import java.util.Collection;

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

  public RmicSettings() {
    DEPRECATION = false; // in this configuration deprecation is false by default
  }

  public Collection<String> getOptions(Chunk<Module> chunk) {
    final Collection<String> options = super.getOptions(chunk);
    if(GENERATE_IIOP_STUBS) {
      options.add("-iiop");
    }
    return options;
  }

  protected boolean acceptUserOption(String token) {
    if (!super.acceptUserOption(token)) {
      return false;
    }
    return !("-iiop".equals(token));
  }

  protected boolean acceptEncoding() {
    return false;
  }
}