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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.server.BuildManager;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.ClassObject;
import com.intellij.openapi.compiler.CompilationException;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

// todo: consider batching compilations in order not to start a separate process for every class that needs to be compiled
public class CompilingEvaluatorImpl extends CompilingEvaluator {

  private final EvaluationContextImpl myEvaluationContext;

  public CompilingEvaluatorImpl(EvaluationContextImpl evaluationContext, @NotNull PsiElement context, @NotNull ExtractLightMethodObjectHandler.ExtractedData data) {
    super(context, data);
    myEvaluationContext = evaluationContext;
  }

  @Override
  @NotNull
  protected Collection<ClassObject> compile(@Nullable JavaSdkVersion debuggeeVersion) throws EvaluateException {
    final Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleUtilCore.findModuleForPsiElement(myPsiContext);
      }
    });
    final List<String> options = new ArrayList<>();
    options.add("-encoding");
    options.add("UTF-8");
    final List<File> platformClasspath = new ArrayList<>();
    final List<File> classpath = new ArrayList<>();
    AnnotationProcessingConfiguration profile = null;
    if (module != null) {
      profile = CompilerConfiguration.getInstance(module.getProject()).getAnnotationProcessingConfiguration(module);
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      for (String s : rootManager.orderEntries().compileOnly().recursively().exportedOnly().withoutSdk().getPathsList().getPathList()) {
        classpath.add(new File(s));
      }
      for (String s : rootManager.orderEntries().compileOnly().sdkOnly().getPathsList().getPathList()) {
        platformClasspath.add(new File(s));
      }
    }
    JavaBuilder.addAnnotationProcessingOptions(options, profile);

    final Pair<Sdk, JavaSdkVersion> runtime = BuildManager.getBuildProcessRuntimeSdk(myEvaluationContext.getProject());
    final JavaSdkVersion buildRuntimeVersion = runtime.getSecond();
    // if compiler or debuggee version or both are unknown, let source and target be the compiler's defaults
    if (buildRuntimeVersion != null && debuggeeVersion != null) {
      final JavaSdkVersion minVersion = buildRuntimeVersion.ordinal() > debuggeeVersion.ordinal() ? debuggeeVersion : buildRuntimeVersion;
      final String sourceOption = getSourceOption(minVersion.getMaxLanguageLevel());
      options.add("-source");
      options.add(sourceOption);
      options.add("-target");
      options.add(sourceOption);
    }

    final CompilerManager compilerManager = CompilerManager.getInstance(myEvaluationContext.getProject());

    File sourceFile = null;
    try {
      sourceFile = generateTempSourceFile(compilerManager.getJavacCompilerWorkingDir());
      final File srcDir = sourceFile.getParentFile();
      final List<File> sourcePath = Collections.emptyList();
      final Set<File> sources = Collections.singleton(sourceFile);

      return compilerManager.compileJavaCode(options, platformClasspath, classpath, Collections.emptyList(), sourcePath, sources, srcDir);
    }
    catch (CompilationException e) {
      final StringBuilder res = new StringBuilder("Compilation failed:\n");
      for (CompilationException.Message m : e.getMessages()) {
        if (m.getCategory() == CompilerMessageCategory.ERROR) {
          res.append(m.getText()).append("\n");
        }
      }
      throw new EvaluateException(res.toString());
    }
    catch (Exception e) {
      throw new EvaluateException(e.getMessage());
    }
    finally {
      if (sourceFile != null) {
        FileUtil.delete(sourceFile);
      }
    }
  }

  @NotNull
  private static String getSourceOption(@NotNull LanguageLevel languageLevel) {
    return "1." + Integer.valueOf(3 + languageLevel.ordinal());
  }

  private File generateTempSourceFile(File workingDir) throws IOException {
    final Pair<String, String> fileData = ApplicationManager.getApplication().runReadAction((Computable<Pair<String, String>>)() -> {
      PsiFile file = myData.getGeneratedInnerClass().getContainingFile();
      return Pair.create(file.getName(), file.getText());
    });
    if (fileData.first == null) {
      throw new IOException("Class file name not specified");
    }
    if (fileData.second == null) {
      throw new IOException("Class source code not specified");
    }
    final File file = new File(workingDir, "debugger/src/"+fileData.first);
    FileUtil.writeToFile(file, fileData.second);
    return file;
  }
}
