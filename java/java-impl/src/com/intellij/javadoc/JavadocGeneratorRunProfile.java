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
package com.intellij.javadoc;

import com.intellij.analysis.AnalysisScope;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.PathsList;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class JavadocGeneratorRunProfile implements ModuleRunProfile {
  private static final Logger LOGGER = Logger.getInstance(JavadocConfiguration.class);
  private final Project myProject;
  private final AnalysisScope myGenerationScope;
  private final JavadocConfiguration myConfiguration;

  public JavadocGeneratorRunProfile(Project project, AnalysisScope generationScope, JavadocConfiguration configuration) {
    myProject = project;
    myGenerationScope = generationScope;
    myConfiguration = configuration;
  }

  public static Sdk getSdk(@NotNull Project project) {
    return PathUtilEx.getAnyJdk(project);
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new MyJavaCommandLineState(myConfiguration, myProject, myGenerationScope, env);
  }

  public String getName() {
    return JavadocBundle.message("javadoc.settings.title");
  }

  public Icon getIcon() {
    return null;
  }

  private static class MyJavaCommandLineState extends CommandLineState {
    private final AnalysisScope myGenerationOptions;
    private final Project myProject;
    @NonNls private static final String INDEX_HTML = "index.html";
    private JavadocConfiguration myConfiguration;

    public MyJavaCommandLineState(final JavadocConfiguration configuration,
                                  Project project,
                                  AnalysisScope generationOptions,
                                  ExecutionEnvironment env) {
      super(env);
      myGenerationOptions = generationOptions;
      myProject = project;
      addConsoleFilters(new RegexpFilter(project, "$FILE_PATH$:$LINE$:[^\\^]+\\^"),
                        new RegexpFilter(project, "$FILE_PATH$:$LINE$: warning - .+$"));
      this.myConfiguration = configuration;
    }

    protected GeneralCommandLine createCommandLine() throws ExecutionException {
      final GeneralCommandLine cmdLine = new GeneralCommandLine();
      final Sdk jdk = getSdk(myProject);
      setupExeParams(jdk, cmdLine);
      setupProgramParameters(jdk, cmdLine);
      return cmdLine;
    }

    private void setupExeParams(final Sdk jdk, GeneralCommandLine cmdLine) throws ExecutionException {
      final String jdkPath =
        jdk != null && jdk.getSdkType() instanceof JavaSdkType ? ((JavaSdkType)jdk.getSdkType()).getBinPath(jdk) : null;
      if (jdkPath == null) {
        throw new CantRunException(JavadocBundle.message("javadoc.generate.no.jdk.path"));
      }
      JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
      if (myConfiguration.HEAP_SIZE != null && myConfiguration.HEAP_SIZE.trim().length() != 0) {
        if (version == null || version.isAtLeast(JavaSdkVersion.JDK_1_2)) {
          cmdLine.getParametersList().prepend("-J-Xmx" + myConfiguration.HEAP_SIZE + "m");
        }
        else {
          cmdLine.getParametersList().prepend("-J-mx" + myConfiguration.HEAP_SIZE + "m");
        }
      }
      cmdLine.setWorkDirectory((File)null);
      @NonNls final String javadocExecutableName = File.separator + (SystemInfo.isWindows ? "javadoc.exe" : "javadoc");
      @NonNls String exePath = jdkPath.replace('/', File.separatorChar) + javadocExecutableName;
      if (new File(exePath).exists()) {
        cmdLine.setExePath(exePath);
      }
      else { //try to use wrapper jdk
        exePath = new File(jdkPath).getParent().replace('/', File.separatorChar) + javadocExecutableName;
        if (!new File(exePath).exists()) {
          final File parent = new File(System.getProperty("java.home")).getParentFile(); //try system jre
          exePath = parent.getPath() + File.separator + "bin" + javadocExecutableName;
          if (!new File(exePath).exists()) {
            throw new CantRunException(JavadocBundle.message("javadoc.generate.no.jdk.path"));
          }
        }
        cmdLine.setExePath(exePath);
      }
    }

    private void setupProgramParameters(final Sdk jdk, final GeneralCommandLine cmdLine) throws CantRunException {
      @NonNls final ParametersList parameters = cmdLine.getParametersList();

      if (myConfiguration.LOCALE != null && myConfiguration.LOCALE.length() > 0) {
        parameters.add("-locale");
        parameters.add(myConfiguration.LOCALE);
      }

      if (myConfiguration.OPTION_SCOPE != null) {
        parameters.add("-" + myConfiguration.OPTION_SCOPE);
      }

      if (!myConfiguration.OPTION_HIERARCHY) {
        parameters.add("-notree");
      }

      if (!myConfiguration.OPTION_NAVIGATOR) {
        parameters.add("-nonavbar");
      }

      if (!myConfiguration.OPTION_INDEX) {
        parameters.add("-noindex");
      }
      else if (myConfiguration.OPTION_SEPARATE_INDEX) {
        parameters.add("-splitindex");
      }

      if (myConfiguration.OPTION_DOCUMENT_TAG_USE) {
        parameters.add("-use");
      }

      if (myConfiguration.OPTION_DOCUMENT_TAG_AUTHOR) {
        parameters.add("-author");
      }

      if (myConfiguration.OPTION_DOCUMENT_TAG_VERSION) {
        parameters.add("-version");
      }

      if (!myConfiguration.OPTION_DOCUMENT_TAG_DEPRECATED) {
        parameters.add("-nodeprecated");
      }
      else if (!myConfiguration.OPTION_DEPRECATED_LIST) {
        parameters.add("-nodeprecatedlist");
      }

      parameters.addParametersString(myConfiguration.OTHER_OPTIONS);

      final Set<Module> modules = new LinkedHashSet<>();
      try {
        final File sourcePathTempFile = FileUtil.createTempFile("javadoc", "args.txt", true);
        parameters.add("@" + sourcePathTempFile.getCanonicalPath());
        final PrintWriter writer = new PrintWriter(new FileWriter(sourcePathTempFile));
        try {
          final Collection<String> packages = new HashSet<>();
          final Collection<String> sources = new HashSet<>();
          final Runnable findRunnable = () -> {
            final int scopeType = myGenerationOptions.getScopeType();
            final boolean usePackageNotation = scopeType == AnalysisScope.MODULE ||
                                               scopeType == AnalysisScope.MODULES ||
                                               scopeType == AnalysisScope.PROJECT ||
                                               scopeType == AnalysisScope.DIRECTORY;
            myGenerationOptions.accept(new MyContentIterator(myProject, packages, sources, modules, usePackageNotation));
          };
          if (!ProgressManager
            .getInstance()
            .runProcessWithProgressSynchronously(findRunnable, "Search for sources to generate javadoc in...", true, myProject)) {
            return;
          }
          if (packages.size() + sources.size() == 0) {
            throw new CantRunException(JavadocBundle.message("javadoc.generate.no.classes.in.selected.packages.error"));
          }
          for (String aPackage : packages) {
            writer.println(aPackage);
          }
          //http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#runningjavadoc
          for (String source : sources) {
            writer.println(StringUtil.wrapWithDoubleQuote(source));
          }
          writer.println("-sourcepath");
          OrderEnumerator enumerator = OrderEnumerator.orderEntries(myProject);
          if (!myConfiguration.OPTION_INCLUDE_LIBS) {
            enumerator = enumerator.withoutSdk().withoutLibraries();
          }
          final PathsList pathsList = enumerator.getSourcePathsList();
          final List<VirtualFile> files = pathsList.getRootDirs();
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          final StringBuilder sourcePath = new StringBuilder();
          boolean start = true;
          for (VirtualFile file : files) {
            if (!myGenerationOptions.isIncludeTestSource() && fileIndex.isInTestSourceContent(file)) continue;
            if (start) {
              start = false;
            }
            else {
              sourcePath.append(File.pathSeparator);
            }
            sourcePath.append(file.getPath());
          }
          writer.println(StringUtil.wrapWithDoubleQuote(sourcePath.toString()));
        }
        finally {
          writer.close();
        }
      }
      catch (IOException e) {
        LOGGER.error(e);
      }

      if (myConfiguration.OPTION_LINK_TO_JDK_DOCS) {
        VirtualFile[] docUrls = jdk.getRootProvider().getFiles(JavadocOrderRootType.getInstance());
        for (VirtualFile docUrl : docUrls) {
          parameters.add("-link");
          parameters.add(VfsUtil.toUri(docUrl).toString());
        }
      }

      final PathsList classPath;
      final OrderEnumerator orderEnumerator = ProjectRootManager.getInstance(myProject).orderEntries(modules);
      if (jdk.getSdkType() instanceof JavaSdk) {
        classPath = orderEnumerator.withoutSdk().withoutModuleSourceEntries().getPathsList();
      }
      else {
        //libraries are included into jdk
        classPath = orderEnumerator.withoutModuleSourceEntries().getPathsList();
      }
      final String classPathString = classPath.getPathsString();
      if (classPathString.length() > 0) {
        parameters.add("-classpath");
        parameters.add(classPathString);
      }

      if (myConfiguration.OUTPUT_DIRECTORY != null) {
        parameters.add("-d");
        parameters.add(myConfiguration.OUTPUT_DIRECTORY.replace('/', File.separatorChar));
      }
    }

    @NotNull
    protected OSProcessHandler startProcess() throws ExecutionException {
      final OSProcessHandler handler = JavaCommandLineStateUtil.startProcess(createCommandLine());
      ProcessTerminatedListener.attach(handler, myProject, JavadocBundle.message("javadoc.generate.exited"));
      handler.addProcessListener(new ProcessAdapter() {
        public void processTerminated(@NotNull ProcessEvent event) {
          if (myConfiguration.OPEN_IN_BROWSER) {
            File url = new File(myConfiguration.OUTPUT_DIRECTORY, INDEX_HTML);
            if (url.exists() && event.getExitCode() == 0) {
              BrowserUtil.browse(url);
            }
          }
        }
      });
      return handler;
    }
  }

  private static class MyContentIterator extends PsiRecursiveElementWalkingVisitor {
    private final PsiManager myPsiManager;
    private final Collection<String> myPackages;
    private final Collection<String> mySourceFiles;
    private final Set<Module> myModules;
    private final boolean myUsePackageNotation;

    public MyContentIterator(Project project,
                             Collection<String> packages,
                             Collection<String> sources,
                             Set<Module> modules,
                             boolean canUsePackageNotation) {
      myModules = modules;
      myUsePackageNotation = canUsePackageNotation;
      myPsiManager = PsiManager.getInstance(project);
      myPackages = packages;
      mySourceFiles = sources;
    }

    @Override
    public void visitFile(PsiFile file) {
      final VirtualFile fileOrDir = file.getVirtualFile();
      if (fileOrDir == null) return;
      if (!fileOrDir.isInLocalFileSystem()) return;
      final Module module = ModuleUtilCore.findModuleForFile(fileOrDir, myPsiManager.getProject());
      if (module != null) {
        myModules.add(module);
      }
      if (file instanceof PsiJavaFile) {
        final PsiJavaFile javaFile = (PsiJavaFile)file;
        final String packageName = javaFile.getPackageName();
        if (containsPackagePrefix(module, packageName) ||
            (packageName.length() == 0 && !(javaFile instanceof ServerPageFile)) ||
            !myUsePackageNotation) {
          mySourceFiles.add(FileUtil.toSystemIndependentName(fileOrDir.getPath()));
        }
        else {
          myPackages.add(packageName);
        }
      }
    }

    private static boolean containsPackagePrefix(Module module, String packageFQName) {
      if (module == null) return false;
      for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders(JavaModuleSourceRootTypes.SOURCES)) {
          final String packagePrefix = sourceFolder.getPackagePrefix();
          final int prefixLength = packagePrefix.length();
          if (prefixLength > 0 && packageFQName.startsWith(packagePrefix)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
