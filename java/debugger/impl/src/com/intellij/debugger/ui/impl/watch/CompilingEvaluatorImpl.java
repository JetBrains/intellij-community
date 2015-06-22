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

import com.intellij.compiler.server.BuildManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessAdapter;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.javac.DiagnosticOutputConsumer;
import org.jetbrains.jps.javac.ExternalJavacManager;
import org.jetbrains.jps.javac.OutputFileConsumer;
import org.jetbrains.jps.javac.OutputFileObject;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
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
  protected Collection<OutputFileObject> compile(@Nullable JavaSdkVersion debuggeeVersion) throws EvaluateException {
    final Pair<Sdk, JavaSdkVersion> runtime = BuildManager.getBuildProcessRuntimeSdk(myEvaluationContext.getProject());
    final Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleUtilCore.findModuleForPsiElement(myPsiContext);
      }
    });
    String javaHome = null;
    final Sdk sdk = runtime.getFirst();
    final SdkTypeId type = sdk.getSdkType();
    if (type instanceof JavaSdkType) {
      javaHome = sdk.getHomePath();
    }
    if (javaHome == null) {
      throw new EvaluateException("Was not able to determine JDK for current evaluation context");
    }
    final List<String> options = new ArrayList<String>();
    options.add("-proc:none"); // for our purposes annotation processing is not needed
    options.add("-encoding");
    options.add("UTF-8");
    final List<File> platformClasspath = new ArrayList<File>();
    final List<File> classpath = new ArrayList<File>();
    if (module != null) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      rootManager.orderEntries().compileOnly().recursively().exportedOnly().withoutSdk().getPathsList().addAllFiles(classpath);
      rootManager.orderEntries().compileOnly().sdkOnly().getPathsList().addAllFiles(platformClasspath);
    }

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

    File sourceFile = null;
    final OutputCollector outputSink = new OutputCollector();
    try {
      final ExternalJavacManager javacManager = getJavacManager();
      if (javacManager == null) {
        throw new EvaluateException("Cannot compile java code");
      }
      sourceFile = generateTempSourceFile(javacManager.getWorkingDir());
      final File srcDir = sourceFile.getParentFile();
      final Map<File, Set<File>> output = Collections.singletonMap(srcDir, Collections.singleton(srcDir));
      DiagnosticCollector diagnostic = new DiagnosticCollector();
      final List<String> vmOptions = Collections.emptyList();
      final List<File> sourcePath = Collections.emptyList();
      final Set<File> sources = Collections.singleton(sourceFile);
      boolean compiledOK = javacManager.forkJavac(
        javaHome, -1, vmOptions, options, platformClasspath, classpath, sourcePath, sources, output, diagnostic, outputSink, new JavacCompilerTool(), CanceledStatus.NULL
      );

      if (!compiledOK) {
        final StringBuilder res = new StringBuilder("Compilation failed:\n");
        for (Diagnostic<? extends JavaFileObject> d : diagnostic.getDiagnostics()) {
          if (d.getKind() == Diagnostic.Kind.ERROR) {
            res.append(d.getMessage(Locale.US));
          }
        }
        throw new EvaluateException(res.toString());
      }
    }
    catch (EvaluateException e) {
      throw e;
    }
    catch (Exception e) {
      throw new EvaluateException(e.getMessage());
    }
    finally {
      if (sourceFile != null) {
        FileUtil.delete(sourceFile);
      }
    }
    return outputSink.getCompiledClasses();
  }

  @NotNull
  private static String getSourceOption(@NotNull LanguageLevel languageLevel) {
    return "1." + Integer.valueOf(3 + languageLevel.ordinal());
  }

  private File generateTempSourceFile(File workingDir) throws IOException {
    final Pair<String, String> fileData = ApplicationManager.getApplication().runReadAction(new Computable<Pair<String, String>>() {
      @Override
      public Pair<String, String> compute() {
        final PsiFile file = myData.getGeneratedInnerClass().getContainingFile();
        return Pair.create(file.getName(), file.getText());
      }
    });
    if (fileData.first == null) {
      throw new IOException("Class file name not specified");
    }
    if (fileData.second == null) {
      throw new IOException("Class source code not specified");
    }
    final File file = new File(workingDir, "src/"+fileData.first);
    FileUtil.writeToFile(file, fileData.second);
    return file;
  }

  private static final Key<ExternalJavacManager> JAVAC_MANAGER_KEY = Key.create("_external_java_compiler_manager_");

  @Nullable
  private ExternalJavacManager getJavacManager() throws IOException {
    // need dedicated thread access to be able to cache the manager in the user data
    DebuggerManagerThreadImpl.assertIsManagerThread();

    final DebugProcessImpl debugProcess = myEvaluationContext.getDebugProcess();
    ExternalJavacManager manager = JAVAC_MANAGER_KEY.get(debugProcess);
    if (manager == null && debugProcess.isAttached()) {
      final File compilerWorkingDir = getCompilerWorkingDir();
      if (compilerWorkingDir == null) {
        return null; // should not happen for real projects
      }
      final int listenPort = NetUtils.findAvailableSocketPort();
      manager = new ExternalJavacManager(compilerWorkingDir);
      manager.start(listenPort);
      final ExternalJavacManager _manager = manager;
      debugProcess.addDebugProcessListener(new DebugProcessAdapter() {
        public void processDetached(DebugProcess process, boolean closedByUser) {
          if (process == debugProcess) {
            _manager.stop();
          }
        }
      });
      JAVAC_MANAGER_KEY.set(debugProcess, manager);
    }
    return manager;
  }

  @Nullable
  private File getCompilerWorkingDir() {
    final File projectBuildDir = BuildManager.getInstance().getProjectSystemDirectory(myEvaluationContext.getProject());
    if (projectBuildDir == null) {
      return null;
    }
    final File root = new File(projectBuildDir, "debugger");
    root.mkdirs();
    return root;
  }

  private static class DiagnosticCollector implements DiagnosticOutputConsumer {
    private final List<Diagnostic<? extends JavaFileObject>> myDiagnostics = new ArrayList<Diagnostic<? extends JavaFileObject>>();
    public void outputLineAvailable(String line) {
      // for debugging purposes uncomment this line
      //System.out.println(line);
    }

    public void registerImports(String className, Collection<String> imports, Collection<String> staticImports) {
      // ignore
    }

    public void javaFileLoaded(File file) {
      // ignore
    }

    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      myDiagnostics.add(diagnostic);
    }

    public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
      return myDiagnostics;
    }
  }

  private static class OutputCollector implements OutputFileConsumer {
    private List<OutputFileObject> myClasses = new ArrayList<OutputFileObject>();

    public void save(@NotNull OutputFileObject fileObject) {
      myClasses.add(fileObject);
    }

    public List<OutputFileObject> getCompiledClasses() {
      return myClasses;
    }
  }
}
