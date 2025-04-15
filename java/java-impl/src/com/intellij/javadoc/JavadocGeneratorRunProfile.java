// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.analysis.AnalysisScope;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.BrowserUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JavadocGeneratorRunProfile implements ModuleRunProfile {
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
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    return new MyJavaCommandLineState(myConfiguration, myProject, myGenerationScope, env);
  }

  @Override
  public @NotNull String getName() {
    return JavaBundle.message("javadoc.settings.title");
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  private static final class MyJavaCommandLineState extends CommandLineState {
    private static final String INDEX_HTML = "index.html";

    private final AnalysisScope myGenerationOptions;
    private final Project myProject;
    private final JavadocConfiguration myConfiguration;
    private final ArgumentFileFilter myArgFileFilter = new ArgumentFileFilter();

    MyJavaCommandLineState(JavadocConfiguration configuration, Project project, AnalysisScope generationOptions, ExecutionEnvironment env) {
      super(env);
      myGenerationOptions = generationOptions;
      myProject = project;
      myConfiguration = configuration;
      addConsoleFilters(
        new RegexpFilter(project, "$FILE_PATH$:$LINE$:"),
        myArgFileFilter);
    }

    @Override
    protected @NotNull OSProcessHandler startProcess() throws ExecutionException {
      OSProcessHandler handler = JavaCommandLineStateUtil.startProcess(createCommandLine());
      ProcessTerminatedListener.attach(handler, myProject, JavaBundle.message("javadoc.generate.exited"));
      handler.addProcessListener(new ProcessListener() {
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
        throw new CantRunException(JavaBundle.message("javadoc.generate.no.jdk"));
      }

      cmdLine.setWorkDirectory((File)null);

      String toolName = SystemInfo.isWindows ? "javadoc.exe" : "javadoc";
      File tool = new File(binPath, toolName);
      if (!tool.exists()) {
        tool = new File(new File(binPath).getParent(), toolName);
        if (!tool.exists()) {
          File javaHomeBinPath = new File(new File(System.getProperty("java.home")).getParent(), "bin");
          tool = new File(javaHomeBinPath, toolName);
          if (!tool.exists()) {
            throw new CantRunException(JavaBundle.message("javadoc.generate.no.javadoc.tool", binPath, javaHomeBinPath));
          }
        }
      }
      cmdLine.setExePath(tool.getPath());

      if (myConfiguration.HEAP_SIZE != null && !myConfiguration.HEAP_SIZE.trim().isEmpty()) {
        String param = JavaSdkUtil.isJdkAtLeast(jdk, JavaSdkVersion.JDK_1_2) ? "-J-Xmx" : "-J-mx";
        cmdLine.getParametersList().prepend(param + myConfiguration.HEAP_SIZE + "m");
      }
    }

    /* http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#runningjavadoc */
    private void setParameters(Sdk jdk, GeneralCommandLine cmdLine) throws CantRunException {
      ParametersList parameters = cmdLine.getParametersList();

      if (myConfiguration.LOCALE != null && !myConfiguration.LOCALE.isEmpty()) {
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
        for (String url : jdk.getRootProvider().getUrls(JavadocOrderRootType.getInstance())) {
          parameters.add("-link");
          parameters.add(url);
        }
      }

      if (myConfiguration.OUTPUT_DIRECTORY != null) {
        parameters.add("-d");
        parameters.add(myConfiguration.OUTPUT_DIRECTORY.replace('/', File.separatorChar));
      }

      Set<Module> modules = new LinkedHashSet<>();
      Set<VirtualFile> sources = new HashSet<>();
      Runnable r = () -> myGenerationOptions.accept(new MyContentIterator(myProject, modules, sources));
      String title = JavaBundle.message("javadoc.generate.sources.progress");
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(r, title, true, myProject)) {
        return;
      }
      if (sources.isEmpty()) {
        throw new CantRunException(JavaBundle.message("javadoc.generate.no.classes.in.selected.packages.error"));
      }

      Set<Module> modulesWithoutDescriptor = new HashSet<>(modules);
      Map<Module, VirtualFile> moduleDescriptors = new HashMap<>();
      boolean hasJavaModules = false;
      for (VirtualFile source : sources) {
        if (!PsiJavaModule.MODULE_INFO_FILE.equals(source.getName())) {
          continue;
        }
        hasJavaModules = true;
        Module module = ModuleUtilCore.findModuleForFile(source, myProject);
        if (module != null) {
          moduleDescriptors.put(module, source);
          modulesWithoutDescriptor.remove(module);
        }
      }
      if (hasJavaModules && !modulesWithoutDescriptor.isEmpty()) {
        // So far we can't generate javadoc for each module independently as we have to merge the results into common files,
        // e.g index.html, index-all.html and so on. Moreover, the final javadoc seems obscured in the case when one module contains
        // module-info file but another one is not.
        throw new CantRunException(JavaBundle.message("javadoc.gen.error.modules.without.module.info", modulesWithoutDescriptor.stream()
          .map(m -> "'" + m.getName() + "'").collect(Collectors.joining(","))));
      }

      if (JavaSdkVersionUtil.isAtLeast(jdk, JavaSdkVersion.JDK_11)) {
        for (Module module : modules) {
          LanguageLevel languageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module);
          if (languageLevel.isPreview()) {
            parameters.add(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
            parameters.add("--source", String.valueOf(languageLevel.feature()));
            break;
          }
        }
      }
      
      File argsFile = createTempArgsFile();
      List<VirtualFile> sourceRoots = findSourceRoots(modules);
      List<VirtualFile> classRoots = findClassRoots(modules, jdk);

      Charset cs = CharsetToolkit.getPlatformCharset();
      try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(argsFile), cs))) {
        if (sourceRoots.size() + classRoots.size() > 0) {
          if (hasJavaModules && JavaSdkUtil.isJdkAtLeast(jdk, JavaSdkVersion.JDK_1_9)) {
            if (modules.size() > 1) {
              writer.println("--module-source-path");
              String moduleSourcePath = computeModuleSourcePath(moduleDescriptors);
              if (moduleSourcePath == null) {
                throw new CantRunException(JavaBundle.message("javadoc.gen.error.module.source.path.is.not.evaluated"));
              }
              writer.println(StringUtil.wrapWithDoubleQuote(moduleSourcePath));
            }
            else if (!sourceRoots.isEmpty()) {
              String path = sourceRoots.stream().map(MyJavaCommandLineState::localPath).collect(Collectors.joining(File.pathSeparator));
              writer.println("--source-path");
              writer.println(StringUtil.wrapWithDoubleQuote(path));
            }

            if (!classRoots.isEmpty()) {
              String path = classRoots.stream().map(MyJavaCommandLineState::localPath).collect(Collectors.joining(File.pathSeparator));
              writer.println("--module-path");
              writer.println(StringUtil.wrapWithDoubleQuote(path));
            }
          }
          else {
            // placing source roots on a classpath is perfectly legal and allows generating correct Javadoc
            // when a module without a module-info.java file depends on another module that has one
            Stream<VirtualFile> roots = Stream.concat(sourceRoots.stream(), classRoots.stream());
            String path = roots.map(MyJavaCommandLineState::localPath).collect(Collectors.joining(File.pathSeparator));
            writer.println("-classpath");
            writer.println(StringUtil.wrapWithDoubleQuote(path));

            if (!sourceRoots.isEmpty() && JavaSdkUtil.isJdkAtLeast(jdk, JavaSdkVersion.JDK_18)) {
              //is needed for javadoc snippets only
              String sourcePath = sourceRoots.stream().map(MyJavaCommandLineState::localPath).collect(Collectors.joining(File.pathSeparator));
              writer.println("--source-path");
              writer.println(StringUtil.wrapWithDoubleQuote(sourcePath));
            }
          }
        }

        for (VirtualFile source : sources) {
          writer.println(StringUtil.wrapWithDoubleQuote(source.getPath()));
        }
      }
      catch (FileNotFoundException e) {
        throw new CantRunException(JavaBundle.message("javadoc.generate.temp.file.does.not.exist"), e);
      }
      catch (CantRunException e) {
        FileUtil.delete(argsFile);
        throw e;
      }

      myArgFileFilter.setPath(argsFile.getPath(), cs);
      parameters.add("@" + argsFile.getPath());
      OSProcessHandler.deleteFileOnTermination(cmdLine, argsFile);
      cmdLine.setCharset(cs);
    }

    private static @NotNull File createTempArgsFile() throws CantRunException {
      File argsFile;
      try {
        argsFile = FileUtil.createTempFile("javadoc_args", null);
      }
      catch (IOException e) {
        throw new CantRunException(JavaBundle.message("javadoc.generate.temp.file.error"), e);
      }
      return argsFile;
    }

    private @Unmodifiable @NotNull List<VirtualFile> findSourceRoots(@NotNull Set<Module> modules) {
      OrderEnumerator sourcePathEnumerator = ProjectRootManager.getInstance(myProject).orderEntries(modules);
      if (!myConfiguration.OPTION_INCLUDE_LIBS) {
        sourcePathEnumerator = sourcePathEnumerator.withoutSdk().withoutLibraries();
      }
      if (!myGenerationOptions.isIncludeTestSource()) {
        sourcePathEnumerator = sourcePathEnumerator.productionOnly();
      }
      return sourcePathEnumerator.getSourcePathsList().getRootDirs();
    }

    private @Unmodifiable @NotNull List<VirtualFile> findClassRoots(@NotNull Set<Module> modules, @NotNull Sdk jdk) {
      OrderEnumerator classPathEnumerator = ProjectRootManager.getInstance(myProject).orderEntries(modules).withoutModuleSourceEntries();
      if (jdk.getSdkType() instanceof JavaSdk) {
        classPathEnumerator = classPathEnumerator.withoutSdk();
      }
      if (!myGenerationOptions.isIncludeTestSource()) {
        classPathEnumerator = classPathEnumerator.productionOnly();
      }
      return classPathEnumerator.getPathsList().getRootDirs();
    }

    /**
     * If a project contains multiple jpms modules then we have to form {@code --module-source-path}.
     *
     * @see <a href="https://docs.oracle.com/javase/9/tools/javadoc.htm">javadoc tool guide</a>
     */
    private static @Nullable String computeModuleSourcePath(@NotNull Map<Module, VirtualFile> moduleDescriptors) {
      if (moduleDescriptors.isEmpty()) return null;
      Set<String> moduleSourcePathParts = new SmartHashSet<>();
      for (var entry : moduleDescriptors.entrySet()) {
        String descriptorParentPath = PathUtil.getParentPath(entry.getValue().getPath());
        VirtualFile modulePath = ContainerUtil.find(ModuleRootManager.getInstance(entry.getKey()).getContentRoots(),
                                                    f -> descriptorParentPath.contains(f.getName()));
        if (modulePath == null) return null;
        String moduleSourcePathPart = descriptorParentPath.replace(modulePath.getName(), "*");
        moduleSourcePathParts.add(moduleSourcePathPart);
      }
      return String.join(File.pathSeparator, moduleSourcePathParts);
    }

    private static @NotNull String localPath(@NotNull VirtualFile root) {
      // @argfile require forward slashes in quoted paths
      return VfsUtil.getLocalFile(root).getPath();
    }
  }

  private static class MyContentIterator extends PsiRecursiveElementWalkingVisitor {
    private final PsiManager myPsiManager;
    private final Set<? super Module> myModules;
    private final Set<? super VirtualFile> mySourceFiles;

    MyContentIterator(Project project, Set<? super Module> modules, Set<? super VirtualFile> sources) {
      myPsiManager = PsiManager.getInstance(project);
      myModules = modules;
      mySourceFiles = sources;
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
      if (file instanceof PsiJavaFile && !(file instanceof ServerPageFile)) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null && vFile.isInLocalFileSystem()) {
          mySourceFiles.add(vFile);

          Module module = ModuleUtilCore.findModuleForFile(vFile, myPsiManager.getProject());
          if (module != null) {
            myModules.add(module);
          }
        }
      }
    }
  }
}