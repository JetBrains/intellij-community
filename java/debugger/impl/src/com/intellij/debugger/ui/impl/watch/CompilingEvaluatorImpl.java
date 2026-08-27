// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.server.BuildManager;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.AdditionalContextProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.IncorrectCodeFragmentException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.ClassObject;
import com.intellij.openapi.compiler.CompilationException;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// todo: consider batching compilations in order not to start a separate process for every class that needs to be compiled
public class CompilingEvaluatorImpl extends CompilingEvaluator {
  private Collection<ClassObject> myCompiledClasses;
  private final @NotNull List<Module> myModules;
  private final @Nullable LanguageLevel myLanguageLevel;

  public CompilingEvaluatorImpl(@NotNull Project project,
                                @NotNull PsiElement context,
                                @NotNull LightMethodObjectExtractedData data) {
    super(project, context, data);
    XDebugSession currentSession = XDebuggerManager.getInstance(project).getCurrentSession();
    RunProfile runProfile = currentSession == null ? null : currentSession.getRunProfile();
    myModules = findContextModules(project, context, runProfile);
    Module module = myModules.isEmpty() ? null : myModules.getFirst();
    myLanguageLevel = module == null ? null : LanguageLevelUtil.getEffectiveLanguageLevel(module);
  }

  @ApiStatus.Internal
  public static @NotNull List<Module> findContextModules(@NotNull Project project,
                                                         @NotNull PsiElement context,
                                                         @Nullable RunProfile runProfile) {
    Module contextModule = ModuleUtilCore.findModuleForPsiElement(context);
    if (contextModule != null) {
      return Collections.singletonList(contextModule);
    }

    Set<Module> modules = new LinkedHashSet<>();
    if (runProfile instanceof RunProfileWithCompileBeforeLaunchOption compileBeforeLaunchProfile) {
      for (Module module : compileBeforeLaunchProfile.getModules()) {
        if (!module.isDisposed() && project.equals(module.getProject())) {
          modules.add(module);
        }
      }
    }
    if (!modules.isEmpty()) {
      return new ArrayList<>(modules);
    }

    PsiFile contextFile = context.getContainingFile();
    VirtualFile virtualFile = contextFile == null ? null : contextFile.getVirtualFile();
    if (virtualFile != null) {
      for (OrderEntry orderEntry : ProjectFileIndex.getInstance(project).getOrderEntriesForFile(virtualFile)) {
        Module ownerModule = orderEntry.getOwnerModule();
        if (!ownerModule.isDisposed()) {
          modules.add(ownerModule);
        }
      }
    }
    return new ArrayList<>(modules);
  }

  @Override
  public @NotNull Collection<ClassObject> compile(@Nullable JavaSdkVersion debuggeeVersion) throws EvaluateException {
    if (myCompiledClasses == null) {
      List<String> options = new ArrayList<>();
      options.add("-encoding");
      options.add("UTF-8");
      Set<File> platformClasspath = new LinkedHashSet<>();
      Set<File> classpath = new LinkedHashSet<>();
      ReadAction.runBlocking(() -> {
        AnnotationProcessingConfiguration profile = null;
        for (Module module : myModules) {
          assert myProject.equals(module.getProject()) : module + " is from another project";
          if (profile == null) {
          profile = CompilerConfiguration.getInstance(myProject).getAnnotationProcessingConfiguration(module);
        }
          ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
          for (String s : rootManager.orderEntries().compileOnly().recursively().exportedOnly().withoutSdk().getPathsList().getPathList()) {
            classpath.add(new File(s));
          }
          for (String s : rootManager.orderEntries().compileOnly().sdkOnly().getPathsList().getPathList()) {
            platformClasspath.add(new File(s));
          }}

          if (myLanguageLevel != null && myLanguageLevel.isPreview()) {
            options.add(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
          }

        JavaBuilder.addAnnotationProcessingOptions(options, profile);
      });

      Pair<Sdk, JavaSdkVersion> runtime = BuildManager.getJavacRuntimeSdk(myProject);
      JavaSdkVersion buildRuntimeVersion = runtime.getSecond();
      // if compiler or debuggee version or both are unknown, let source and target be the compiler's defaults
      if (buildRuntimeVersion != null && debuggeeVersion != null) {
        JavaSdkVersion minVersion = debuggeeVersion.compareTo(buildRuntimeVersion) < 0 ? debuggeeVersion : buildRuntimeVersion;
        String sourceOption = JpsJavaSdkType.complianceOption(minVersion.getMaxLanguageLevel().toJavaVersion());
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
          compilerManager.compileJavaCode(options, platformClasspath, classpath, Collections.emptyList(), Collections.emptyList(), sourcePath, sources, srcDir);
      }
      catch (CompilationException e) {
        StringBuilder res = new StringBuilder("Compilation failed:\n");
        for (CompilationException.Message m : e.getMessages()) {
          if (m.getCategory() == CompilerMessageCategory.ERROR) {
            res.append(m.getText()).append("\n");
          }
        }
        throw new IncorrectCodeFragmentException(res.toString());
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

  private File generateTempSourceFile(File workingDir) throws IOException {
    Pair<String, String> fileData = ReadAction.compute(() -> {
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

  public static @Nullable CompilingEvaluator create(@NotNull Project project,
                                                    @Nullable PsiElement psiContext,
                                                    @NotNull Function<? super PsiElement, ? extends PsiCodeFragment> fragmentFactory)
    throws EvaluateException {
    return create(project, psiContext, null, fragmentFactory);
  }

  @ApiStatus.Internal
  public static @Nullable CompilingEvaluator create(@NotNull Project project,
                                                    @Nullable PsiElement psiContext,
                                                    @Nullable String generatedClassName,
                                                    @NotNull Function<? super PsiElement, ? extends PsiCodeFragment> fragmentFactory)
    throws EvaluateException {
    if (Registry.is("debugger.compiling.evaluator") && psiContext != null) {
      return ReadAction.compute(() -> {
        try {
          XDebugSession currentSession = XDebuggerManager.getInstance(project).getCurrentSession();
          JavaSdkVersion javaVersion = getJavaVersion(currentSession);
          PsiElement physicalContext = findPhysicalContext(psiContext);
          PsiCodeFragment fragment = fragmentFactory.apply(psiContext);
          LightMethodObjectExtractedData data = ExtractLightMethodObjectHandler.extractLightMethodObject(
            project,
            physicalContext != null ? physicalContext : psiContext,
            fragment,
            generatedClassName != null ? generatedClassName : getGeneratedClassName(),
            javaVersion,
            generatedClassName,
            findAdditionalContextVariables(fragment));
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

  private static @Nullable PsiElement findPhysicalContext(@NotNull PsiElement element) {
    while (element != null && !element.isPhysical()) {
      element = element.getContext();
    }
    return element;
  }

  private static @NotNull List<PsiLocalVariable> findAdditionalContextVariables(@NotNull PsiCodeFragment fragment) {
    Map<String, PsiLocalVariable> result = new LinkedHashMap<>();
    for (PsiReferenceExpression expression : PsiTreeUtil.findChildrenOfType(fragment, PsiReferenceExpression.class)) {
      PsiElement target = expression.resolve();
      if (target instanceof PsiLocalVariable variable &&
          variable.getUserData(AdditionalContextProvider.getADDITIONAL_CONTEXT_ELEMENT_KEY()) != null) {
        result.putIfAbsent(variable.getName(), variable);
      }
    }
    return new ArrayList<>(result.values());
  }

  public static @Nullable JavaSdkVersion getJavaVersion(@Nullable XDebugSession session) {
    if (session != null) {
      XSuspendContext suspendContext = session.getSuspendContext();
      if (suspendContext instanceof SuspendContextImpl suspendContextImpl) {
        return JavaSdkVersion.fromVersionString(suspendContextImpl.getVirtualMachineProxy().version());
      }
    }

    return null;
  }
}
