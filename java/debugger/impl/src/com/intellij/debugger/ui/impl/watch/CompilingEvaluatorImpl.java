/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CompilingEvaluatorImpl extends CompilingEvaluator {

  public CompilingEvaluatorImpl(@NotNull PsiElement context, @NotNull ExtractLightMethodObjectHandler.ExtractedData data) {
    super(context, data);
  }

  @Override
  @NotNull
  protected Collection<OutputFileObject> compile(String target) throws EvaluateException {
    if (!SystemInfo.isJavaVersionAtLeast(target)) {
      throw new EvaluateException("Unable to compile for target level " + target + ". Need to run IDEA on java version at least " + target + ", currently running on " + SystemInfo.JAVA_RUNTIME_VERSION);
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    MemoryFileManager manager = new MemoryFileManager(compiler);
    DiagnosticCollector<JavaFileObject> diagnostic = new DiagnosticCollector<JavaFileObject>();
    Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleUtilCore.findModuleForPsiElement(myPsiContext);
      }
    });
    List<String> options = new ArrayList<String>();
    if (module != null) {
      options.add("-cp");
      PathsList cp = ModuleRootManager.getInstance(module).orderEntries().compileOnly().recursively().exportedOnly().withoutSdk().getPathsList();
      options.add(cp.getPathsString());
    }
    if (!StringUtil.isEmpty(target)) {
      options.add("-source");
      options.add(target);
      options.add("-target");
      options.add(target);
    }
    try {
      if (!compiler.getTask(null,
                            manager,
                            diagnostic,
                            options,
                            null,
                            Collections.singletonList(new SourceFileObject(getMainClassName(), JavaFileObject.Kind.SOURCE, getClassCode()))
      ).call()) {
        StringBuilder res = new StringBuilder("Compilation failed:\n");
        for (Diagnostic<? extends JavaFileObject> d : diagnostic.getDiagnostics()) {
          res.append(d);
        }
        throw new EvaluateException(res.toString());
      }
    }
    catch (Exception e) {
      throw new EvaluateException(e.getMessage());
    }
    return manager.classes;
  }

  protected String getClassCode() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myData.getGeneratedInnerClass().getContainingFile().getText();
      }
    });
  }

  protected String getMainClassName() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return FileUtil.getNameWithoutExtension(myData.getGeneratedInnerClass().getContainingFile().getName());
      }
    });
  }
}
