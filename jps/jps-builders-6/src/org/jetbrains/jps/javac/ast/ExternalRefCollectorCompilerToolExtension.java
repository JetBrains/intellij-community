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
import org.jetbrains.backwardRefs.javac.ast.api.JavacFileData;
import org.jetbrains.jps.javac.DiagnosticOutputConsumer;

public class ExternalRefCollectorCompilerToolExtension extends AbstractRefCollectorCompilerToolExtension {
  public static final String ID = "external.ast.reference.collector";

  public static final String ENABLED_PARAM = "external.java.process.ref.collector.enabled";
  public static final String DIVIDE_IMPORTS_PARAM = "external.java.process.divide.imports";

  @Override
  protected boolean isEnabled() {
    return "true".equals(System.getProperty(ENABLED_PARAM));
  }

  @Override
  protected boolean divideImportsRefs() {
    return "true".equals(System.getProperty(DIVIDE_IMPORTS_PARAM));
  }

  @NotNull
  @Override
  protected Consumer<JavacFileData> getFileDataConsumer(@NotNull final DiagnosticOutputConsumer diagnosticConsumer) {
    return new Consumer<JavacFileData>() {
      @Override
      public void consume(JavacFileData data) {
        diagnosticConsumer.customOutputData(ID, "javac-refs", data.asBytes());
      }
    };
  }
}
