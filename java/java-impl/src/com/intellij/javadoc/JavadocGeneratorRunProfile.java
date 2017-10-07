// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javadoc;

import com.intellij.analysis.AnalysisScope;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.BrowserUtil;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
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

import static com.intellij.analysis.AnalysisScope.*;

/**
 * @author nik
 */
public class JavadocGeneratorRunProfile implements ModuleRunProfile {
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

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return new MyJavaCommandLineState(myConfiguration, myProject, myGenerationScope, env);
  }

  @Override
  public String getName() {
    return JavadocBundle.message("javadoc.settings.title");
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  private static class MyJavaCommandLineState extends CommandLineState {
    private static final String INDEX_HTML = "index.html";

    private final AnalysisScope myGenerationOptions;
    private final Project myProject;
    private final JavadocConfiguration myConfiguration;
    private final ArgumentFileFilter myArgFileFilter = new ArgumentFileFilter();

    public MyJavaCommandLineState(JavadocConfiguration configuration,
                                  Project project,
                                  AnalysisScope generationOptions,
                                  ExecutionEnvironment env) {
      super(env);
      myGenerationOptions = generationOptions;
      myProject = project;
      myConfiguration = configuration;
      addConsoleFilters(
        new RegexpFilter(project, "$FILE_PATH$:$LINE$:[^\\^]+\\^"),
        new RegexpFilter(project, "$FILE_PATH$:$LINE$: warning - .+$"),
        myArgFileFilter);
    }

    @NotNull
    protected OSProcessHandler startProcess() throws ExecutionException {
      OSProcessHandler handler = JavaCommandLineStateUtil.startProcess(createCommandLine());
      ProcessTerminatedListener.attach(handler, myProject, JavadocBundle.message("javadoc.generate.exited"));
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (myConfiguration.OPEN_IN_BROWSER && event.getExitCode() == 0) {
            File index = new File(myConfiguration.OUTPUT_DIRECTORY, INDEX_HTML);
            if (index.exists()) {
              BrowserUtil.browse(index);
            }
          }
        }
      });
      return handler;
    }

    private GeneralCommandLine createCommandLine() throws ExecutionException {
      GeneralCommandLine cmdLine = new GeneralCommandLine();
      Sdk jdk = getSdk(myProject);
      setExecutable(jdk, cmdLine);
      setParameters(jdk, cmdLine);
      return cmdLine;
    }

    private void setExecutable(Sdk jdk, GeneralCommandLine cmdLine) throws ExecutionException {
      String binPath = jdk != null && jdk.getSdkType() instanceof JavaSdkType ? ((JavaSdkType)jdk.getSdkType()).getBinPath(jdk) : null;
      if (binPath == null) {
        throw new CantRunException(JavadocBundle.message("javadoc.generate.no.jdk.path"));
      }

      cmdLine.setWorkDirectory((File)null);

      String toolName = SystemInfo.isWindows ? "javadoc.exe" : "javadoc";
      File tool = new File(binPath, toolName);
      if (!tool.exists()) {
        tool = new File(new File(binPath).getParent(), toolName);
        if (!tool.exists()) {
          tool = new File(new File(System.getProperty("java.home")).getParent(), "bin/" + toolName);
          if (!tool.exists()) {
            throw new CantRunException(JavadocBundle.message("javadoc.generate.no.jdk.path"));
          }
        }
      }
      cmdLine.setExePath(tool.getPath());

      if (myConfiguration.HEAP_SIZE != null && myConfiguration.HEAP_SIZE.trim().length() != 0) {
        JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
        String param = version == null || version.isAtLeast(JavaSdkVersion.JDK_1_2) ? "-J-Xmx" : "-J-mx";
        cmdLine.getParametersList().prepend(param + myConfiguration.HEAP_SIZE + "m");
      }
    }

    /* http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#runningjavadoc */
    private void setParameters(Sdk jdk, GeneralCommandLine cmdLine) throws CantRunException {
      ParametersList parameters = cmdLine.getParametersList();

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

      if (myConfiguration.OPTION_LINK_TO_JDK_DOCS) {
        VirtualFile[] docUrls = jdk.getRootProvider().getFiles(JavadocOrderRootType.getInstance());
        for (VirtualFile docUrl : docUrls) {
          parameters.add("-link");
          parameters.add(VfsUtil.toUri(docUrl).toString());
        }
      }

      if (myConfiguration.OUTPUT_DIRECTORY != null) {
        parameters.add("-d");
        parameters.add(myConfiguration.OUTPUT_DIRECTORY.replace('/', File.separatorChar));
      }

      Set<Module> modules = new LinkedHashSet<>();
      try {
        File argsFile = FileUtil.createTempFile("javadoc_args", null);

        try (PrintWriter writer = new PrintWriter(new FileWriter(argsFile))) {
          Set<String> packages = new HashSet<>();
          Set<String> sources = new HashSet<>();
          int t = myGenerationOptions.getScopeType();
          boolean usePackageNotation = t == MODULE || t == MODULES || t == PROJECT || t == DIRECTORY;
          Runnable r = () -> myGenerationOptions.accept(new MyContentIterator(myProject, modules, packages, sources, usePackageNotation));
          String title = JavadocBundle.message("javadoc.generate.sources.progress");
          if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(r, title, true, myProject)) {
            return;
          }
          if (packages.size() + sources.size() == 0) {
            throw new CantRunException(JavadocBundle.message("javadoc.generate.no.classes.in.selected.packages.error"));
          }

          for (String pkg : packages) {
            writer.println(pkg);
          }
          for (String src : sources) {
            writer.println(StringUtil.wrapWithDoubleQuote(src));
          }

          OrderEnumerator rootEnumerator = OrderEnumerator.orderEntries(myProject);
          if (!myConfiguration.OPTION_INCLUDE_LIBS) {
            rootEnumerator = rootEnumerator.withoutSdk().withoutLibraries();
          }
          List<VirtualFile> files = rootEnumerator.getSourcePathsList().getRootDirs();
          if (!myGenerationOptions.isIncludeTestSource()) {
            ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
            files = ContainerUtil.filter(files, f -> !index.isInTestSourceContent(f));
          }
          if (!files.isEmpty()) {
            writer.println("-sourcepath");
            writer.println(StringUtil.wrapWithDoubleQuote(StringUtil.join(files, File.pathSeparator)));
          }

          OrderEnumerator classPathEnumerator = ProjectRootManager.getInstance(myProject).orderEntries(modules).withoutModuleSourceEntries();
          if (jdk.getSdkType() instanceof JavaSdk) {
            classPathEnumerator = classPathEnumerator.withoutSdk();
          }
          String classPath = classPathEnumerator.getPathsList().getPathsString();
          if (!classPath.isEmpty()) {
            writer.println("-classpath");
            writer.println(StringUtil.wrapWithDoubleQuote(classPath));
          }
        }

        myArgFileFilter.setPath(argsFile.getPath());
        parameters.add("@" + argsFile.getPath());
        OSProcessHandler.deleteFileOnTermination(cmdLine, argsFile);
      }
      catch (IOException e) {
        throw new CantRunException(JavadocBundle.message("javadoc.generate.temp.file.error"), e);
      }
    }
  }

  private static class MyContentIterator extends PsiRecursiveElementWalkingVisitor {
    private final PsiManager myPsiManager;
    private final Set<Module> myModules;
    private final Set<String> myPackages;
    private final Collection<String> mySourceFiles;
    private final boolean myUsePackageNotation;

    public MyContentIterator(Project project, Set<Module> modules, Set<String> packages, Collection<String> sources, boolean usePackageNotation) {
      myPsiManager = PsiManager.getInstance(project);
      myModules = modules;
      myPackages = packages;
      mySourceFiles = sources;
      myUsePackageNotation = usePackageNotation;
    }

    @Override
    public void visitFile(PsiFile file) {
      VirtualFile vFile = file.getVirtualFile();
      if (vFile != null && vFile.isInLocalFileSystem()) {
        Module module = ModuleUtilCore.findModuleForFile(vFile, myPsiManager.getProject());
        if (module != null) {
          myModules.add(module);
        }
        if (file instanceof PsiJavaFile && !(file instanceof ServerPageFile)) {
          String packageName = ((PsiJavaFile)file).getPackageName();
          if (!myUsePackageNotation || packageName.isEmpty() || containsPackagePrefix(module, packageName)) {
            mySourceFiles.add(FileUtil.toSystemIndependentName(vFile.getPath()));
          }
          else {
            myPackages.add(packageName);
          }
        }
      }
    }

    private static boolean containsPackagePrefix(Module module, String packageFQName) {
      if (module != null) {
        for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
          for (SourceFolder sourceFolder : contentEntry.getSourceFolders(JavaModuleSourceRootTypes.SOURCES)) {
            String packagePrefix = sourceFolder.getPackagePrefix();
            if (!packagePrefix.isEmpty() && packageFQName.startsWith(packagePrefix)) {
              return true;
            }
          }
        }
      }

      return false;
    }
  }
}