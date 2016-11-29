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
import org.jetbrains.jps.javac.DiagnosticOutputConsumer;
import org.jetbrains.jps.javac.JavaCompilerToolExtension;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import javax.tools.*;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 09-Nov-16
 */
public class RefCollectorCompilerToolExtension extends JavaCompilerToolExtension{
  public static final String ID = "ASTReferenceCollector";

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public void beforeCompileTaskExecution(@NotNull JavaCompilingTool compilingTool,
                                         @NotNull JavaCompiler.CompilationTask task,
                                         @NotNull Collection<String> options,
                                         @NotNull DiagnosticOutputConsumer diagnosticConsumer) {
    // todo: transfer collected data via DiagnosticOutputConsumer.customOutputData()
    if (JavaCompilers.JAVAC_ID.equals(compilingTool.getId())) {
      JavacReferencesCollector.installOn(task);
    }
  }

  @Override
  public void processData(String dataName, byte[] content) {
    // todo!
  }
}
