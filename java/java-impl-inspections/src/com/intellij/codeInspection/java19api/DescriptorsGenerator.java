// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.ide.fileTemplates.JavaTemplateUtil.INTERNAL_MODULE_INFO_TEMPLATE_NAME;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_CLASS;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;

class DescriptorsGenerator {
  private final Logger myLogger;
  private final Project myProject;
  private final UniqueModuleNames myUniqueModuleNames;

  private final ProgressTracker myProgressTracker = new ProgressTracker(0.5, 0.3, 0.2);

  DescriptorsGenerator(@NotNull Project project, @NotNull UniqueModuleNames uniqueModuleNames, @NotNull Logger logger) {
    myProject = project;
    myUniqueModuleNames = uniqueModuleNames;
    myLogger = logger;
  }

  void generate(@NotNull List<ModuleFiles> moduleFiles, @NotNull ProgressIndicator indicator) {
    myProgressTracker.init(indicator);
    List<ModuleInfo> modulesInfos;
    try {
      myProgressTracker.startPhase(JavaRefactoringBundle.message("generate.module.descriptors.collecting.message"),
                                   moduleFiles.stream().mapToInt(m -> m.files().size()).sum());
      Map<String, Set<ModuleNode>> packagesDeclaredInModules = collectDependencies(moduleFiles);
      myProgressTracker.nextPhase();

      final int modulesCount = packagesDeclaredInModules.values().stream().mapToInt(Set::size).sum();

      myProgressTracker.startPhase(JavaRefactoringBundle.message("generate.module.descriptors.analysing.message"), modulesCount);
      final List<ModuleNode> modules = prepareModulesWithDependencies(packagesDeclaredInModules);
      myProgressTracker.nextPhase();

      myProgressTracker.startPhase(JavaRefactoringBundle.message("generate.module.descriptors.preparing.message"), modulesCount);
      modulesInfos = prepareModuleInfos(modules);
      myProgressTracker.nextPhase();
    }
    finally {
      myProgressTracker.dispose();
    }
    createFilesLater(modulesInfos);
  }

  private void createFilesLater(@NotNull List<ModuleInfo> moduleInfos) {
    final Runnable createFiles = () -> {
      if (myProject.isDisposed()) return;
      CommandProcessor.getInstance().executeCommand(myProject, () ->
        ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(
          getCommandTitle(), myProject, null,
          indicator -> createFiles(myProject, moduleInfos, indicator)), getCommandTitle(), null);
    };

    if (CoreProgressManager.shouldKeepTasksAsynchronous()) {
      ApplicationManager.getApplication().invokeLater(createFiles);
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(createFiles);
    }
  }

  @NotNull
  private Map<String, Set<ModuleNode>> collectDependencies(@NotNull List<ModuleFiles> modulesFiles) {
    PackageNamesCache packageNamesCache = new PackageNamesCache(myProject);
    Map<String, Set<ModuleNode>> packagesDeclaredInModules = new HashMap<>();

    for (ModuleFiles moduleFiles : modulesFiles) {
      ModuleVisitor visitor = new ModuleVisitor(packageNamesCache::getPackageName);
      if (moduleFiles.files().isEmpty()) {
        myLogger.info("Output directory for module " + moduleFiles.module().getName() + " doesn't contain .class files");
        continue;
      }
      for (Path file : moduleFiles.files) {
        visitor.processFile(file.toFile());
        myProgressTracker.increment();
      }
      Set<String> declaredPackages = visitor.getDeclaredPackages();
      Set<String> requiredPackages = visitor.getRequiredPackages();

      ModuleNode moduleNode = new ModuleNode(moduleFiles.module(), declaredPackages, requiredPackages, myUniqueModuleNames);
      for (String declaredPackage : declaredPackages) {
        packagesDeclaredInModules.computeIfAbsent(declaredPackage, key -> new HashSet<>()).add(moduleNode);
      }
    }
    return packagesDeclaredInModules;
  }

  @NotNull
  private List<ModuleNode> prepareModulesWithDependencies(@NotNull Map<String, Set<ModuleNode>> packagesDeclaredInModules) {
    List<ModuleNode> modules = packagesDeclaredInModules.values().stream()
      .flatMap(Collection::stream).toList();

    Map<PsiJavaModule, ModuleNode> nodesByDescriptor = new HashMap<>();
    for (ModuleNode module : modules) {
      if (module.getDescriptor() != null) {
        nodesByDescriptor.put(module.getDescriptor(), module);
      }

      for (String packageName : module.getRequiredPackages()) {
        Set<ModuleNode> set = packagesDeclaredInModules.get(packageName);
        if (set == null) {
          PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
          if (psiPackage == null) continue;
          PsiJavaModule descriptor = ReadAction.compute(() -> findDescriptor(psiPackage));
          if (descriptor == null) continue;
          set = Set.of(nodesByDescriptor.computeIfAbsent(descriptor, ModuleNode::new));
        }
        else if (set.size() != 1) {
          myLogger.info("Split package " + packageName + " found in " + set);
        }
        module.getDependencies().addAll(set);
      }
      myProgressTracker.increment();
    }
    return modules;
  }

  @NotNull
  private List<ModuleInfo> prepareModuleInfos(@NotNull List<ModuleNode> modules) {
    Set<String> requiredPackages = modules.stream()
      .map(ModuleNode::getRequiredPackages)
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());

    List<ModuleInfo> moduleInfo = new ArrayList<>();
    for (ModuleNode moduleNode : modules) {
      if (moduleNode.getDescriptor() != null) {
        myLogger.info("Module descriptor already exists in " + moduleNode);
        continue;
      }
      for (String packageName : moduleNode.getDeclaredPackages()) {
        if (requiredPackages.contains(packageName)) {
          moduleNode.addExport(packageName);
        }
      }

      PsiDirectory rootDir = moduleNode.getRootDir();
      if (rootDir != null) {
        moduleInfo.add(new ModuleInfo(rootDir, moduleNode.getName(), moduleNode.getDependencies(), moduleNode.getExports()));
      }
      else {
        myLogger.info("Skipped module " + moduleNode + " because it doesn't have production source root");
      }
      myProgressTracker.increment();
    }
    return moduleInfo;
  }

  private void createFiles(@NotNull Project project, @NotNull List<ModuleInfo> moduleInfos, @NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    int count = 0;
    double total = moduleInfos.size();
    FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_MODULE_INFO_TEMPLATE_NAME);
    for (ModuleInfo moduleInfo : moduleInfos) {
      ProgressManager.getInstance().executeNonCancelableSection(() -> createFile(template, moduleInfo));
      indicator.setFraction(++count / total);
    }
  }

  private void createFile(@NotNull FileTemplate template, @NotNull ModuleInfo moduleInfo) {
    if (moduleInfo.fileAlreadyExists()) return;
    Project project = moduleInfo.rootDir().getProject();
    Properties properties = FileTemplateManager.getInstance(project).getDefaultProperties();
    FileTemplateUtil.fillDefaultProperties(properties, moduleInfo.rootDir());
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, MODULE_INFO_CLASS);
    try {
      PsiJavaFile moduleInfoFile = // this is done to copy the file header to the output
        (PsiJavaFile)FileTemplateUtil.createFromTemplate(template, MODULE_INFO_FILE, properties, moduleInfo.rootDir());
      PsiJavaModule javaModule = moduleInfoFile.getModuleDeclaration();
      myLogger.assertTrue(javaModule != null, "module-info file should contain module declaration");

      CharSequence moduleText = moduleInfo.createModuleText();
      PsiJavaFile dummyFile = (PsiJavaFile)PsiFileFactory.getInstance(project)
        .createFileFromText(MODULE_INFO_FILE, JavaLanguage.INSTANCE, moduleText);
      PsiJavaModule actualModule = dummyFile.getModuleDeclaration();
      myLogger.assertTrue(actualModule != null, "module declaration wasn't created");
      javaModule.replace(actualModule);
      CodeStyleManager.getInstance(project).reformat(moduleInfoFile);
    }
    catch (Exception e) {
      myLogger.error("Failed to create module-info.java in " + moduleInfo.rootDir().getVirtualFile().getPath() + ": " + e.getMessage());
    }
  }

  @Nullable
  private static PsiJavaModule findDescriptor(@NotNull PsiPackage psiPackage) {
    PsiManager psiManager = psiPackage.getManager();
    for (PsiDirectory directory : psiPackage.getDirectories()) {
      PsiDirectory managerDirectory = psiManager.findDirectory(directory.getVirtualFile());
      if (managerDirectory != null) {
        return JavaModuleGraphUtil.findDescriptorByElement(managerDirectory);
      }
    }
    return null;
  }

  private static @NlsContexts.Command String getCommandTitle() {
    return JavaRefactoringBundle.message("generate.module.descriptors.command.title");
  }

  private static class PackageNamesCache {
    private final Map<String, Boolean> myPackages = new HashMap<>();
    private final JavaPsiFacade myPsiFacade;

    PackageNamesCache(@NotNull Project project) {
      myPsiFacade = JavaPsiFacade.getInstance(project);
    }

    @Nullable
    private String getPackageName(@NotNull String className) {
      int dotPos;
      while ((dotPos = className.lastIndexOf('.')) > 0) {
        className = className.substring(0, dotPos);
        Boolean isPackage = myPackages.computeIfAbsent(className, packageName -> ReadAction.compute(() -> myPsiFacade.findPackage(packageName) != null));
        if (isPackage) return className;
      }
      return null;
    }
  }

  record ModuleFiles(@NotNull Module module, @NotNull List<Path> files) {
  }
}
