// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.AbstractDependencyVisitor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Pavel.Dolgov
 */
public class Java9GenerateModuleDescriptorsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(Java9GenerateModuleDescriptorsAction.class);

  private static final String TITLE = "Generate Module Descriptors";
  private static final String COMMAND_TITLE = "Creating module-info Files";

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && !DumbService.isDumb(project));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = compilerManager.createProjectCompileScope(project);
    if (!compilerManager.isUpToDate(scope)) {
      int result = Messages.showYesNoCancelDialog(project,
                                                  "The project needs to be built for better accuracy of dependencies calculation. \n" +
                                                  "Start the build before generating module-info descriptors?",
                                                  TITLE, null);
      if (result == Messages.CANCEL) {
        return;
      }
      if (result == Messages.YES) {
        compilerManager.compile(scope, (aborted, errors, warnings, compileContext) -> {
          if (!aborted && errors == 0) {
            generate(project);
          }
        });
        return;
      }
    }
    generate(project);
  }

  private static void generate(Project project) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      THashMap<Module, List<File>> classFiles = new THashMap<>();
      int totalFiles = collectClassFiles(project, classFiles);
      if (totalFiles != 0) {
        new DescriptorsGenerator(project).generate(classFiles, totalFiles);
      }
    }, TITLE, true, project);
  }

  private static int collectClassFiles(@NotNull Project project, @NotNull Map<Module, List<File>> classFiles) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setIndeterminate(true);
    indicator.setText("Scanning Compiler Output");

    Module[] modules = StreamEx.of(ModuleManager.getInstance(project).getModules())
                               .filter(module -> mayContainModuleInfo(module))
                               .toArray(Module.EMPTY_ARRAY);
    if (modules.length == 0) {
      CommonRefactoringUtil.showErrorHint(project, null, "Found no modules which may contain module-info", TITLE, null);
      return 0;
    }

    int totalFiles = 0;
    for (Module module : modules) {
      List<File> moduleClasses = new ArrayList<>();
      classFiles.put(module, moduleClasses);

      String production = CompilerPaths.getModuleOutputPath(module, false);
      File productionRoot = production != null ? new File(production) : null;
      if (productionRoot != null) {
        collectClassFiles(productionRoot, moduleClasses);
      }
      totalFiles += moduleClasses.size();
    }
    if (totalFiles == 0) {
      CommonRefactoringUtil
        .showErrorHint(project, null, "Couldn't generate module descriptors because the project hasn't been built yet", TITLE, null);
    }
    return totalFiles;
  }

  private static boolean mayContainModuleInfo(Module module) {
    return ReadAction.compute(() -> {
      ModuleRootManager moduleManager = ModuleRootManager.getInstance(module);
      PsiManager psiManager = PsiManager.getInstance(module.getProject());
      return StreamEx.of(moduleManager.getSourceRoots(false))
                     .map(psiManager::findDirectory)
                     .nonNull()
                     .anyMatch(psiDir -> PsiUtil.getLanguageLevel(psiDir).isAtLeast(LanguageLevel.JDK_1_9));
    });
  }

  private static void collectClassFiles(@NotNull File file, @NotNull List<File> files) {
    final File[] children = file.listFiles();
    if (children != null) { // is Directory
      for (File child : children) {
        collectClassFiles(child, files);
      }
    }
    else if (file.getName().endsWith(CommonClassNames.CLASS_FILE_EXTENSION)) {
      files.add(file);
    }
  }

  private static class ProgressTracker {
    ProgressIndicator myIndicator = ProgressManager.getInstance().getProgressIndicator();

    int myCount;
    int mySize;
    int myPhase;
    double myUpToNow;
    private double[] myPhases;

    public ProgressTracker(double... phases) {
      myPhases = phases;

      myIndicator.setFraction(0);
      myIndicator.setIndeterminate(false);
    }

    void startPhase(String text, int size) {
      myIndicator.setText(text);
      myCount = 0;
      mySize = Math.min(size, 1);
    }

    public void nextPhase() {
      myUpToNow += myPhases[myPhase++];
    }

    public void increment() {
      myIndicator.setFraction(myUpToNow + myPhases[myPhase] * ++myCount / (double)mySize);
    }

    public void dispose() {
      myIndicator = null;
    }
  }

  private static class DescriptorsGenerator {
    private final Project myProject;

    private final List<ModuleNode> myModuleNodes = new ArrayList<>();
    private final Set<String> myUsedExternallyPackages = new THashSet<>();

    private final ProgressTracker myProgressTracker = new ProgressTracker(0.5, 0.3, 0.2);

    public DescriptorsGenerator(Project project) {
      myProject = project;
    }

    void generate(THashMap<Module, List<File>> classFiles, int totalFiles) {
      List<GeneratedCode> generatedCode;
      try {
        myProgressTracker.startPhase("Collecting Dependencies", totalFiles);
        Map<String, Set<ModuleNode>> packagesDeclaredInModules = collectDependencies(classFiles);
        myProgressTracker.nextPhase();

        myProgressTracker.startPhase("Analysing Dependencies", myModuleNodes.size());
        analyseDependencies(packagesDeclaredInModules);
        myProgressTracker.nextPhase();

        myProgressTracker.startPhase("Preparing Code", myModuleNodes.size());
        generatedCode = generateCode();
        myProgressTracker.nextPhase();
      }
      finally {
        myProgressTracker.dispose();
      }
      createFilesLater(generatedCode);
    }

    private void createFilesLater(List<GeneratedCode> generatedCode) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myProject.isDisposed()) {
          CommandProcessor.getInstance().executeCommand(myProject, () ->
            ((ApplicationImpl)ApplicationManager.getApplication()).runWriteActionWithCancellableProgressInDispatchThread(
              COMMAND_TITLE, myProject, null,
              indicator -> createFiles(myProject, generatedCode, indicator)), COMMAND_TITLE, null);
        }
      });
    }

    private Map<String, Set<ModuleNode>> collectDependencies(THashMap<Module, List<File>> classFiles) {
      PackageNamesCache packageNamesCache = new PackageNamesCache(myProject);
      Map<String, Set<ModuleNode>> packagesDeclaredInModules = new THashMap<>();

      for (Map.Entry<Module, List<File>> entry : classFiles.entrySet()) {
        Module module = entry.getKey();

        ModuleVisitor visitor = new ModuleVisitor(packageNamesCache);
        for (File file : entry.getValue()) {
          visitor.processFile(file);
          myProgressTracker.increment();
        }
        Set<String> declaredPackages = visitor.getDeclaredPackages();
        Set<String> requiredPackages = visitor.getRequiredPackages();
        requiredPackages.removeAll(declaredPackages);

        myUsedExternallyPackages.addAll(requiredPackages);
        ModuleNode moduleNode = new ModuleNode(module, declaredPackages, requiredPackages);
        myModuleNodes.add(moduleNode);
        for (String declaredPackage : declaredPackages) {
          packagesDeclaredInModules.computeIfAbsent(declaredPackage, __ -> new THashSet<>()).add(moduleNode);
        }
      }
      return packagesDeclaredInModules;
    }

    private void analyseDependencies(Map<String, Set<ModuleNode>> packagesDeclaredInModules) {
      Map<PsiJavaModule, ModuleNode> nodesByDescriptor = new THashMap<>();
      for (ModuleNode moduleNode : myModuleNodes) {
        if (moduleNode.getDescriptor() != null) {
          nodesByDescriptor.put(moduleNode.getDescriptor(), moduleNode);
        }

        for (String packageName : moduleNode.getRequiredPackages()) {
          Set<ModuleNode> set = packagesDeclaredInModules.get(packageName);
          if (set == null) {
            PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
            if (psiPackage != null) {
              PsiJavaModule descriptor = ReadAction.compute(() -> findDescriptor(psiPackage));
              if (descriptor != null) {
                ModuleNode dependencyNode = nodesByDescriptor.computeIfAbsent(descriptor, ModuleNode::new);
                moduleNode.getDependencies().add(dependencyNode);
              }
            }
          }
          else {
            if (set.size() != 1) {
              LOG.debug("Split package " + packageName + " in " + set);
            }
            moduleNode.getDependencies().addAll(set);
          }
        }
        myProgressTracker.increment();
      }
    }

    private List<GeneratedCode> generateCode() {
      List<GeneratedCode> generatedCode = new ArrayList<>();
      for (ModuleNode moduleNode : myModuleNodes) {
        if (moduleNode.getDescriptor() != null) {
          LOG.debug("Descriptor already exists in " + moduleNode);
          continue;
        }
        for (String packageName : moduleNode.getDeclaredPackages()) {
          if (myUsedExternallyPackages.contains(packageName)) {
            moduleNode.getExports().add(packageName);
          }
        }

        StringBuilder text = new StringBuilder();
        text.append("module ").append(moduleNode.getName()).append(" {");
        List<ModuleNode> sortedDependencies = moduleNode.getSortedDependencies();
        List<String> sortedExports = moduleNode.getSortedExports();
        for (ModuleNode dependencyNode : sortedDependencies) {
          if (!"java.base".equals(dependencyNode.getName())) {
            text.append("\n requires ").append(dependencyNode.getName()).append(";");
          }
        }
        if (!sortedDependencies.isEmpty() && !sortedExports.isEmpty()) {
          text.append('\n');
        }
        for (String packageName : sortedExports) {
          text.append("\n exports ").append(packageName).append(";");
        }
        text.append("\n}");

        PsiDirectory rootDir = moduleNode.getRootDir();
        if (rootDir != null) {
          generatedCode.add(new GeneratedCode(rootDir, text.toString()));
        }
        else {
          LOG.debug("Skipped module " + moduleNode);
        }
        myProgressTracker.increment();
      }
      return generatedCode;
    }

    private static void createFiles(Project project, List<GeneratedCode> generatedCode, ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      int count = 0;
      double total = generatedCode.size();
      PsiFileFactory factory = PsiFileFactory.getInstance(project);
      for (GeneratedCode code : generatedCode) {
        ProgressManager.getInstance().executeNonCancelableSection(() -> {
          PsiFile file = factory.createFileFromText(PsiJavaModule.MODULE_INFO_FILE, JavaLanguage.INSTANCE, code.myText);
          PsiElement added = code.myRootDir.add(file);
          CodeStyleManager.getInstance(project).reformat(added);
        });
        indicator.setFraction(++count / total);
      }
    }

    @Nullable
    private static PsiJavaModule findDescriptor(PsiPackage psiPackage) {
      PsiManager psiManager = psiPackage.getManager();
      return StreamEx.of(psiPackage.getDirectories())
                     .map(PsiDirectory::getVirtualFile)
                     .nonNull()
                     .map(psiManager::findDirectory)
                     .nonNull()
                     .findAny()
                     .map(JavaModuleGraphUtil::findDescriptorByElement)
                     .orElse(null);
    }
  }

  private static class ModuleNode implements Comparable<ModuleNode> {
    private final Module myModule;
    private final Set<String> myDeclaredPackages;
    private final Set<String> myRequiredPackages;
    private final Set<ModuleNode> myDependencies = new THashSet<>();
    private final Set<String> myExports = new THashSet<>();
    private final PsiJavaModule myDescriptor;
    private final String myName;

    public ModuleNode(@NotNull Module module, @NotNull Set<String> declaredPackages, @NotNull Set<String> requiredPackages) {
      myModule = module;
      myDeclaredPackages = declaredPackages;
      myRequiredPackages = requiredPackages;

      myDescriptor = ReadAction.compute(() -> {
        VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
        return sourceRoots.length != 0 ? findDescriptor(module, sourceRoots[0]) : null;
      });
      myName = myDescriptor != null ? myDescriptor.getName() : LightJavaModule.moduleName(myModule.getName());
    }

    @Nullable
    private static PsiJavaModule findDescriptor(@NotNull Module module, VirtualFile root) {
      return JavaModuleGraphUtil.findDescriptorByElement(PsiManager.getInstance(module.getProject()).findDirectory(root));
    }

    public ModuleNode(@NotNull PsiJavaModule descriptor) {
      myModule = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(descriptor));
      myDeclaredPackages = Collections.emptySet();
      myRequiredPackages = Collections.emptySet();
      myDescriptor = descriptor;
      myName = myDescriptor.getName();
    }

    public Set<String> getDeclaredPackages() {
      return myDeclaredPackages;
    }

    public Set<String> getRequiredPackages() {
      return myRequiredPackages;
    }

    public Set<ModuleNode> getDependencies() {
      return myDependencies;
    }

    public List<ModuleNode> getSortedDependencies() {
      ArrayList<ModuleNode> list = new ArrayList<>(myDependencies);
      list.sort(ModuleNode::compareTo);
      return list;
    }

    public Set<String> getExports() {
      return myExports;
    }

    public List<String> getSortedExports() {
      ArrayList<String> list = new ArrayList<>(myExports);
      list.sort(String::compareTo);
      return list;
    }

    public PsiJavaModule getDescriptor() {
      return myDescriptor;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Override
    public String toString() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof ModuleNode && myName.equals(((ModuleNode)o).myName);
    }

    @Override
    public int hashCode() {
      return myName.hashCode();
    }

    @Override
    public int compareTo(@NotNull ModuleNode o) {
      int m1 = myModule == null ? 0 : 1, m2 = o.myModule == null ? 0 : 1;
      if (m1 != m2) return m1 - m2;
      int j1 = myName.startsWith("java.") || myName.startsWith("javax.") ? 0 : 1;
      int j2 = o.myName.startsWith("java.") || o.myName.startsWith("javax.") ? 0 : 1;
      if (j1 != j2) return j1 - j2;
      return StringUtil.compare(myName, o.myName, false);
    }

    public PsiDirectory getRootDir() {
      if (myModule == null) return null;
      return ReadAction.compute(() -> {
        ModuleRootManager moduleManager = ModuleRootManager.getInstance(myModule);
        PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
        return findJavaDirectory(psiManager, moduleManager.getSourceRoots(false));
      });
    }

    @Nullable
    private static PsiDirectory findJavaDirectory(PsiManager psiManager, VirtualFile[] roots) {
      return StreamEx.of(roots)
                     .sorted(Comparator.comparingInt((VirtualFile vFile) -> "java".equals(vFile.getName()) ? 0 : 1)
                                       .thenComparing(VirtualFile::getName))
                     .map(psiManager::findDirectory)
                     .nonNull()
                     .findFirst()
                     .orElse(null);
    }
  }

  private static class PackageNamesCache {
    private final Map<String, Boolean> myPackages = new THashMap<>();
    private final JavaPsiFacade myPsiFacade;

    public PackageNamesCache(Project project) {
      myPsiFacade = JavaPsiFacade.getInstance(project);
    }

    private String getPackageName(String className) {
      for (int dotPos = className.lastIndexOf('.'); dotPos > 0; dotPos = className.lastIndexOf('.', dotPos - 1)) {
        String packageName = className.substring(0, dotPos);
        Boolean isPackage = myPackages.computeIfAbsent(packageName, this::isExistingPackage);
        if (isPackage) {
          return packageName;
        }
      }
      return null;
    }

    @NotNull
    private Boolean isExistingPackage(String packageName) {
      return ReadAction.compute(() -> myPsiFacade.findPackage(packageName) != null);
    }
  }

  private static class ModuleVisitor extends AbstractDependencyVisitor {
    private final Set<String> myRequiredPackages = new THashSet<>();
    private final Set<String> myDeclaredPackages = new THashSet<>();
    private final PackageNamesCache myPackageNamesCache;

    public ModuleVisitor(PackageNamesCache packageNamesCache) {
      myPackageNamesCache = packageNamesCache;
    }

    @Override
    protected void addClassName(String className) {
      String packageName = myPackageNamesCache.getPackageName(className);
      if (packageName != null) {
        myRequiredPackages.add(packageName);
      }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      super.visit(version, access, name, signature, superName, interfaces);

      String packageName = myPackageNamesCache.getPackageName(getCurrentClassName());
      if (packageName != null) {
        myDeclaredPackages.add(packageName);
      }
    }

    public Set<String> getRequiredPackages() {
      return myRequiredPackages;
    }

    public Set<String> getDeclaredPackages() {
      return myDeclaredPackages;
    }
  }

  private static class GeneratedCode {
    final PsiDirectory myRootDir;
    final String myText;

    private GeneratedCode(@NotNull PsiDirectory rootDir, @NotNull String text) {
      myRootDir = rootDir;
      myText = text;
    }
  }
}
