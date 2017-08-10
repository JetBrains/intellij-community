/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.javac.DiagnosticOutputConsumer;
import org.jetbrains.jps.javac.ast.api.JavacFileData;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.ArrayList;
import java.util.List;

public class InProcessRefCollectorCompilerToolExtension extends AbstractRefCollectorCompilerToolExtension {
  @Override
  protected boolean isEnabled() {
    try {
      @SuppressWarnings("unused")
      final Class<JpsServiceManager> jpsServiceManager = JpsServiceManager.class;
    }
    catch (NoClassDefFoundError ignored) {
      return false;
    }

    for (JavacFileReferencesRegistrar registrar : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
      if (registrar.isEnabled()) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean divideImportsRefs() {
    return false;
  }

  @NotNull
  @Override
  protected Consumer<JavacFileData> getFileDataConsumer(@NotNull DiagnosticOutputConsumer diagnosticConsumer) {
    return createFileDataConsumer();
  }

  @NotNull
  static Consumer<JavacFileData> createFileDataConsumer() {
    return new JavacFileDataConsumer();
  }

  private static class JavacFileDataConsumer implements Consumer<JavacFileData> {
    final JavacFileReferencesRegistrar[] myRegistrars;

    private JavacFileDataConsumer() {
      List<JavacFileReferencesRegistrar> registrars = new ArrayList<>();
      for (JavacFileReferencesRegistrar registrar : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
        if (registrar.isEnabled()) {
          registrar.initialize();
          registrars.add(registrar);
        }
      }
      myRegistrars = registrars.toArray(new JavacFileReferencesRegistrar[registrars.size()]);
    }

    @Override
    public void consume(JavacFileData data) {
      for (JavacFileReferencesRegistrar registrar : myRegistrars) {
        if (registrar.isEnabled()) {
          registrar.registerFile(data.getFilePath(), registrar.onlyImports() ? data.getImportRefs() : data.getRefs(), data.getDefs(), data.getCasts());
        }
      }
    }
  }
}
