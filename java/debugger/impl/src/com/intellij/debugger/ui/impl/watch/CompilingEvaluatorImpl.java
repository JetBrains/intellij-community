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
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.ClassObject;
import com.intellij.openapi.compiler.CompilationException;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

// todo: consider batching compilations in order not to start a separate process for every class that needs to be compiled
public class CompilingEvaluatorImpl extends CompilingEvaluator {
  private Collection<ClassObject> myCompiledClasses;

  public CompilingEvaluatorImpl(@NotNull Project project,
                                @NotNull PsiElement context,
                                @NotNull ExtractLightMethodObjectHandler.ExtractedData data) {
    super(project, context, data);
  }

  @Override
  @NotNull
  protected Collection<ClassObject> compile(@Nullable JavaSdkVersion debuggeeVersion) throws EvaluateException {
    if (myCompiledClasses == null) {
      Module module = ApplicationManager.getApplication().runReadAction(
        (Computable<Module>)() -> ModuleUtilCore.findModuleForPsiElement(myPsiContext));
      List<String> options = new ArrayList<>();
      options.add("-encoding");
      options.add("UTF-8");
      List<File> platformClasspath = new ArrayList<>();
      List<File> classpath = new ArrayList<>();
      AnnotationProcessingConfiguration profile = null;
      if (module != null) {
        assert myProject.equals(module.getProject()) : module + " is from another project";
        profile = CompilerConfiguration.getInstance(myProject).getAnnotationProcessingConfiguration(module);
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        for (String s : rootManager.orderEntries().compileOnly().recursively().exportedOnly().withoutSdk().getPathsList().getPathList()) {
          classpath.add(new File(s));
        }
        for (String s : rootManager.orderEntries().compileOnly().sdkOnly().getPathsList().getPathList()) {
          platformClasspath.add(new File(s));
        }
      }
      JavaBuilder.addAnnotationProcessingOptions(options, profile);

      Pair<Sdk, JavaSdkVersion> runtime = BuildManager.getJavacRuntimeSdk(myProject);
      JavaSdkVersion buildRuntimeVersion = runtime.getSecond();
      // if compiler or debuggee version or both are unknown, let source and target be the compiler's defaults
      if (buildRuntimeVersion != null && debuggeeVersion != null) {
        JavaSdkVersion minVersion = buildRuntimeVersion.ordinal() > debuggeeVersion.ordinal() ? debuggeeVersion : buildRuntimeVersion;
        String sourceOption = getSourceOption(minVersion.getMaxLanguageLevel());
        options.add("-source");
        options.add(sourceOption);
        options.add("-target");
        options.add(sourceOption);
      }

      CompilerManager compilerManager = CompilerManager.getInstance(myProject);

      File sourceFile = null;
      try {
        sourceFile = generateTempSourceFile(compilerManager.getJavacCompilerWorkingDir());
        File srcDir = sourceFile.getParentFile();
        List<File> sourcePath = Collections.emptyList();
        Set<File> sources = Collections.singleton(sourceFile);

        myCompiledClasses =
          compilerManager.compileJavaCode(options, platformClasspath, classpath, Collections.emptyList(), sourcePath, sources, srcDir);
      }
      catch (CompilationException e) {
        StringBuilder res = new StringBuilder("Compilation failed:\n");
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
    return myCompiledClasses;
  }

  @NotNull
  private static String getSourceOption(@NotNull LanguageLevel languageLevel) {
    return "1." + Integer.valueOf(3 + languageLevel.ordinal());
  }

  private File generateTempSourceFile(File workingDir) throws IOException {
    Pair<String, String> fileData = ApplicationManager.getApplication().runReadAction((Computable<Pair<String, String>>)() -> {
      PsiFile file = myData.getGeneratedInnerClass().getContainingFile();
      return Pair.create(file.getName(), file.getText());
    });
    if (fileData.first == null) {
      throw new IOException("Class file name not specified");
    }
    if (fileData.second == null) {
      throw new IOException("Class source code not specified");
    }
    File file = new File(workingDir, "debugger/src/" + fileData.first);
    FileUtil.writeToFile(file, fileData.second);
    return file;
  }

  @Nullable
  public static ExpressionEvaluator create(@NotNull Project project,
                                           @Nullable PsiElement psiContext,
                                           @NotNull Function<PsiElement, PsiCodeFragment> fragmentFactory)
    throws EvaluateException {
    if (Registry.is("debugger.compiling.evaluator") && psiContext != null) {
      return ApplicationManager.getApplication().runReadAction((ThrowableComputable<ExpressionEvaluator, EvaluateException>)() -> {
        try {
          ExtractLightMethodObjectHandler.ExtractedData data = ExtractLightMethodObjectHandler.extractLightMethodObject(
            project,
            psiContext.getContainingFile(),
            fragmentFactory.apply(psiContext),
            getGeneratedClassName());
          if (data != null) {
            return new CompilingEvaluatorImpl(project, psiContext, data);
          }
        }
        catch (PrepareFailedException e) {
          NodeDescriptorImpl.LOG.info(e);
        }
        return null;
      });
    }
    return null;
  }
}
