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

import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.javac.DiagnosticOutputConsumer;
import org.jetbrains.jps.javac.JavaCompilerToolExtension;
import org.jetbrains.jps.javac.ast.api.JavacFileData;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;
import org.jetbrains.jps.service.JpsServiceManager;

import javax.tools.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 09-Nov-16
 */
public class RefCollectorCompilerToolExtension extends JavaCompilerToolExtension{
  public static final String ID = "ASTReferenceCollector";

  private static final String ENABLED_PARAM = "jps.ref.collector.enabled";
  private static final String DIVIDE_IMPORTS_PARAM = "jps.divide.imports";

  private final AtomicClearableLazyValue<List<JavacFileReferencesRegistrar>> myRegistrars = new AtomicClearableLazyValue<List<JavacFileReferencesRegistrar>>() {
    @NotNull
    @Override
    protected List<JavacFileReferencesRegistrar> compute() {
      List<JavacFileReferencesRegistrar> result = new ArrayList<JavacFileReferencesRegistrar>();
      for (JavacFileReferencesRegistrar registrar : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
        if (registrar.isEnabled()) {
          registrar.initialize();
          result.add(registrar);
        }
      }
      return result;
    }
  };

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public void beforeCompileTaskExecution(@NotNull JavaCompilingTool compilingTool,
                                         @NotNull JavaCompiler.CompilationTask task,
                                         @NotNull Collection<String> options,
                                         @NotNull final DiagnosticOutputConsumer diagnosticConsumer) {
    if (isJavac(compilingTool)) {
      final String isEnabledProperty = System.getProperty(ENABLED_PARAM);

      Consumer<JavacFileData> fileDataConsumer;
      boolean divideImportsRefs;
      if (isEnabledProperty == null) {
        //in-process
        final RefCollectorOptions refCollectorOptions = new RefCollectorOptions();
        fileDataConsumer = refCollectorOptions.enabled ? new Consumer<JavacFileData>() {
          @Override
          public void consume(JavacFileData data) {
            submitFileData(data);
          }
        } : null;
        divideImportsRefs = refCollectorOptions.divideImportRefs;
      } else if (isEnabledProperty.equals("true")) {
        //external
        fileDataConsumer = new Consumer<JavacFileData>() {
          @Override
          public void consume(JavacFileData data) {
            diagnosticConsumer.customOutputData(ID, "javac-refs", data.asBytes());
          }
        };
        divideImportsRefs = SystemProperties.getBooleanProperty(DIVIDE_IMPORTS_PARAM, false);
      } else {
        return;
      }

      if (fileDataConsumer != null) {
        JavacReferenceCollector.installOn(task, divideImportsRefs, fileDataConsumer);
      }
    }
  }

  @Override
  public List<String> getExternalBuildProcessOptions(@NotNull JavaCompilingTool compilingTool) {
    List<String> options = new ArrayList<String>(2);
    if (isJavac(compilingTool)) {
      final RefCollectorOptions refCollectorOptions = new RefCollectorOptions();
      options.add("-D" + ENABLED_PARAM + "=" + refCollectorOptions.enabled);
      if (refCollectorOptions.enabled && refCollectorOptions.divideImportRefs) {
        options.add("-D" + DIVIDE_IMPORTS_PARAM + "=true");
      }
    } else {
      options.add("-D" + ENABLED_PARAM + "=false");
    }
    return options;
  }

  @Override
  public void processData(String dataName, byte[] content) {
    submitFileData(JavacFileData.fromBytes(content));
  }

  private void submitFileData(@NotNull JavacFileData data) {
    for (JavacFileReferencesRegistrar registrar : myRegistrars.getValue()) {
      registrar.registerFile(data.getFilePath(), registrar.onlyImports() ? data.getImportRefs() : data.getRefs(), data.getDefs());
    }
  }

  private static boolean isJavac(@NotNull JavaCompilingTool compilingTool) {
    return JavaCompilers.JAVAC_ID.equals(compilingTool.getId());
  }

  private static class RefCollectorOptions {
    private final boolean enabled;
    private final boolean divideImportRefs;

    private RefCollectorOptions() {
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

      this.enabled = enabled;
      this.divideImportRefs = divideImportRefs;
    }
  }

  @TestOnly
  public void clearRegistrars() {
    myRegistrars.drop();
  }
}
