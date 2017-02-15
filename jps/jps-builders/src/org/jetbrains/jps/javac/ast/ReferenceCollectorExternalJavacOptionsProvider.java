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
package org.jetbrains.jps.javac.ast;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.incremental.java.ExternalJavacOptionsProvider;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ReferenceCollectorExternalJavacOptionsProvider implements ExternalJavacOptionsProvider {
  @NotNull
  @Override
  public Collection<String> getOptions(@NotNull JavaCompilingTool tool) {
    List<String> options = new ArrayList<>(2);
    if (tool.getId().equals(JavaCompilers.JAVAC_ID)) {
      final JavacReferenceCollectorOptions
        javacReferenceCollectorOptions = new JavacReferenceCollectorOptions();
      options.add("-D" + ExternalRefCollectorCompilerToolExtension.ENABLED_PARAM + "=" + javacReferenceCollectorOptions.isEnabled());
      if (javacReferenceCollectorOptions.isEnabled() && javacReferenceCollectorOptions.isImportRefsDivided()) {
        options.add("-D" + ExternalRefCollectorCompilerToolExtension.DIVIDE_IMPORTS_PARAM + "=true");
      }
    }
    return options;
  }

  private static class JavacReferenceCollectorOptions {
    private final boolean myEnabled;
    private final boolean myDivideImportRefs;

    private JavacReferenceCollectorOptions() {
      boolean enabled = false;
      boolean divideImportRefs = false;
      for (JavacFileReferencesRegistrar listener : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
        if (listener.isEnabled()) {
          enabled = true;
          if (listener.onlyImports()) {
            divideImportRefs = true;
          }
        }
      }

      myEnabled = enabled;
      myDivideImportRefs = divideImportRefs;
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public boolean isImportRefsDivided() {
      return myDivideImportRefs;
    }
  }
}
