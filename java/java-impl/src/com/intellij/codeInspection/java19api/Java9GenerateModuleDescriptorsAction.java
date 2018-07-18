// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.AbstractDependencyVisitor;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.lexer.JavaLexer;
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
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import static com.intellij.ide.fileTemplates.JavaTemplateUtil.INTERNAL_MODULE_INFO_TEMPLATE_NAME;
import static com.intellij.psi.PsiJavaModule.*;

/**
 * @author Pavel.Dolgov
 */
public class Java9GenerateModuleDescriptorsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(Java9GenerateModuleDescriptorsAction.class);

  private static final String TITLE = RefactoringBundle.message("generate.module.descriptors.title");
  private static final String COMMAND_TITLE = RefactoringBundle.message("generate.module.descriptors.command.title");

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && !DumbService.isDumb(project) && isModularJdkAvailable());
  }

  private static boolean isModularJdkAvailable() {
    return Arrays.stream(ProjectJdkTable.getInstance().getAllJdks())
                 .anyMatch(sdk -> JavaSdkUtil.isJdkAtLeast(sdk, JavaSdkVersion.JDK_1_9));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = compilerManager.createProjectCompileScope(project);
    if (!compilerManager.isUpToDate(scope)) {
      int result = Messages.showYesNoCancelDialog(
        project, RefactoringBundle.message("generate.module.descriptors.rebuild.message"), TITLE, null);
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

  private static void generate(@NotNull Project project) {
    DumbService.getInstance(project).smartInvokeLater(
      () -> generateWhenSmart(project, new UniqueModuleNames(project)));
  }

  private static void generateWhenSmart(@NotNull Project project, @NotNull UniqueModuleNames uniqueModuleNames) {
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, TITLE, true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          THashMap<Module, List<File>> classFiles = new THashMap<>();
          int totalFiles = collectClassFiles(project, classFiles);
          if (totalFiles != 0) {
            new DescriptorsGenerator(project, uniqueModuleNames).generate(classFiles, indicator, totalFiles);
          }
        }
      });
  }

  private static int collectClassFiles(@NotNull Project project, @NotNull Map<Module, List<File>> classFiles) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setIndeterminate(true);
    indicator.setText(RefactoringBundle.message("generate.module.descriptors.scanning.message"));

    Module[] modules = StreamEx.of(ModuleManager.getInstance(project).getModules())
                               .filter(module -> mayContainModuleInfo(module))
                               .toArray(Module.EMPTY_ARRAY);
    if (modules.length == 0) {
      CommonRefactoringUtil.showErrorHint(
        project, null, RefactoringBundle.message("generate.module.descriptors.no.suitable.modules.message"), TITLE, null);
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
      CommonRefactoringUtil.showErrorHint(
        project, null, RefactoringBundle.message("generate.module.descriptors.build.required.message"), TITLE, null);
    }
    return totalFiles;
  }

  private static boolean mayContainModuleInfo(Module module) {
    return ReadAction.compute(() ->
      EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module).isAtLeast(LanguageLevel.JDK_1_9)
    );
  }

  private static void collectClassFiles(@NotNull File file, @NotNull List<? super File> files) {
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
    ProgressIndicator myIndicator;

    int myCount;
    int mySize;
    int myPhase;
    double myUpToNow;
    private final double[] myPhases;

    public ProgressTracker(double... phases) {
      myPhases = phases;
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

    public void init(ProgressIndicator indicator) {
      myIndicator = indicator;
      myIndicator.setFraction(0);
      myIndicator.setIndeterminate(false);
    }

    public void dispose() {
      myIndicator = null;
    }
  }

  private static class DescriptorsGenerator {
    private final Project myProject;
    private final UniqueModuleNames myUniqueModuleNames;

    private final List<ModuleNode> myModuleNodes = new ArrayList<>();
    private final Set<String> myUsedExternallyPackages = new THashSet<>();

    private final ProgressTracker myProgressTracker = new ProgressTracker(0.5, 0.3, 0.2);

    public DescriptorsGenerator(@NotNull Project project, @NotNull UniqueModuleNames uniqueModuleNames) {
      myProject = project;
      myUniqueModuleNames = uniqueModuleNames;
    }

    void generate(THashMap<Module, List<File>> classFiles, ProgressIndicator indicator, int totalFiles) {
      myProgressTracker.init(indicator);
      List<ModuleInfo> moduleInfos;
      try {
        myProgressTracker.startPhase(RefactoringBundle.message("generate.module.descriptors.collecting.message"), totalFiles);
        Map<String, Set<ModuleNode>> packagesDeclaredInModules = collectDependencies(classFiles);
        myProgressTracker.nextPhase();

        myProgressTracker.startPhase(RefactoringBundle.message("generate.module.descriptors.analysing.message"), myModuleNodes.size());
        analyseDependencies(packagesDeclaredInModules);
        myProgressTracker.nextPhase();

        myProgressTracker.startPhase(RefactoringBundle.message("generate.module.descriptors.preparing.message"), myModuleNodes.size());
        moduleInfos = prepareModuleInfos();
        myProgressTracker.nextPhase();
      }
      finally {
        myProgressTracker.dispose();
      }
      createFilesLater(moduleInfos);
    }

    private void createFilesLater(List<ModuleInfo> moduleInfos) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myProject.isDisposed()) {
          CommandProcessor.getInstance().executeCommand(myProject, () ->
            ((ApplicationImpl)ApplicationManager.getApplication()).runWriteActionWithCancellableProgressInDispatchThread(
              COMMAND_TITLE, myProject, null,
              indicator -> createFiles(myProject, moduleInfos, indicator)), COMMAND_TITLE, null);
        }
      });
    }

    private Map<String, Set<ModuleNode>> collectDependencies(THashMap<Module, List<File>> classFiles) {
      PackageNamesCache packageNamesCache = new PackageNamesCache(myProject);
      Map<String, Set<ModuleNode>> packagesDeclaredInModules = new THashMap<>();

      for (Map.Entry<Module, List<File>> entry : classFiles.entrySet()) {
        Module module = entry.getKey();

        ModuleVisitor visitor = new ModuleVisitor(packageNamesCache);
        List<File> files = entry.getValue();
        if (files.isEmpty()) {
          LOG.info("Output directory for module " + module.getName() + " doesn't contain .class files");
          continue;
        }
        for (File file : files) {
          visitor.processFile(file);
          myProgressTracker.increment();
        }
        Set<String> declaredPackages = visitor.getDeclaredPackages();
        Set<String> requiredPackages = visitor.getRequiredPackages();
        requiredPackages.removeAll(declaredPackages);

        myUsedExternallyPackages.addAll(requiredPackages);
        ModuleNode moduleNode = new ModuleNode(module, declaredPackages, requiredPackages, myUniqueModuleNames);
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
              LOG.info("Split package " + packageName + " found in " + set);
            }
            moduleNode.getDependencies().addAll(set);
          }
        }
        myProgressTracker.increment();
      }
    }

    private List<ModuleInfo> prepareModuleInfos() {
      List<ModuleInfo> moduleInfo = new ArrayList<>();
      for (ModuleNode moduleNode : myModuleNodes) {
        if (moduleNode.getDescriptor() != null) {
          LOG.info("Module descriptor already exists in " + moduleNode);
          continue;
        }
        for (String packageName : moduleNode.getDeclaredPackages()) {
          if (myUsedExternallyPackages.contains(packageName)) {
            moduleNode.getExports().add(packageName);
          }
        }

        PsiDirectory rootDir = moduleNode.getRootDir();
        if (rootDir != null) {
          List<String> dependencies = StreamEx.of(moduleNode.getSortedDependencies())
                                              .map(ModuleNode::getName)
                                              .filter(name -> !JAVA_BASE.equals(name))
                                              .toList();

          List<String> exports = moduleNode.getSortedExports();
          moduleInfo.add(new ModuleInfo(rootDir, moduleNode.getName(), dependencies, exports));
        }
        else {
          LOG.info("Skipped module " + moduleNode + " because it doesn't have production source root");
        }
        myProgressTracker.increment();
      }
      return moduleInfo;
    }

    private static void createFiles(Project project, List<? extends ModuleInfo> moduleInfos, ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      int count = 0;
      double total = moduleInfos.size();
      FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_MODULE_INFO_TEMPLATE_NAME);
      for (ModuleInfo moduleInfo : moduleInfos) {
        ProgressManager.getInstance().executeNonCancelableSection(() -> createFile(template, moduleInfo));
        indicator.setFraction(++count / total);
      }
    }

    private static void createFile(FileTemplate template, ModuleInfo moduleInfo) {
      if (moduleInfo.fileAlreadyExists()) {
        return;
      }
      Project project = moduleInfo.myRootDir.getProject();
      Properties properties = FileTemplateManager.getInstance(project).getDefaultProperties();
      FileTemplateUtil.fillDefaultProperties(properties, moduleInfo.myRootDir);
      properties.setProperty(FileTemplate.ATTRIBUTE_NAME, MODULE_INFO_CLASS);
      try {
        PsiJavaFile moduleInfoFile = // this is done to copy the file header to the output
          (PsiJavaFile)FileTemplateUtil.createFromTemplate(template, MODULE_INFO_FILE, properties, moduleInfo.myRootDir);
        PsiJavaModule javaModule = moduleInfoFile.getModuleDeclaration();
        LOG.assertTrue(javaModule != null, "module-info file should contain module declaration");

        CharSequence moduleText = moduleInfo.createModuleText();
        PsiJavaFile dummyFile = (PsiJavaFile)PsiFileFactory.getInstance(project)
                                                           .createFileFromText(MODULE_INFO_FILE, JavaLanguage.INSTANCE, moduleText);
        PsiJavaModule actualModule = dummyFile.getModuleDeclaration();
        LOG.assertTrue(actualModule != null, "module declaration wasn't created");
        javaModule.replace(actualModule);
        CodeStyleManager.getInstance(project).reformat(moduleInfoFile);
      }
      catch (Exception e) {
        LOG.error("Failed to create module-info.java in " + moduleInfo.myRootDir.getVirtualFile().getPath() + ": " + e.getMessage());
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

    public ModuleNode(@NotNull Module module,
                      @NotNull Set<String> declaredPackages,
                      @NotNull Set<String> requiredPackages,
                      @NotNull UniqueModuleNames uniqueModuleNames) {
      myModule = module;
      myDeclaredPackages = declaredPackages;
      myRequiredPackages = requiredPackages;

      myDescriptor = ReadAction.compute(() -> {
        VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
        return sourceRoots.length != 0 ? findDescriptor(module, sourceRoots[0]) : null;
      });
      myName = myDescriptor != null ? myDescriptor.getName() : uniqueModuleNames.getUniqueName(myModule);
    }

    @Nullable
    private static PsiJavaModule findDescriptor(@NotNull Module module, VirtualFile root) {
      return JavaModuleGraphUtil.findDescriptorByFile(root, module.getProject());
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

  public static class NameConverter { // "public" is for tests
    private static final Pattern NON_NAME = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern DOT_SEQUENCE = Pattern.compile("\\.{2,}");
    private static final Pattern SINGLE_DOT = Pattern.compile("\\.");

    @NotNull
    public static String convertModuleName(@NotNull String name) {
      // All non-alphanumeric characters ([^A-Za-z0-9]) are replaced with a dot (".") ...
      name = NON_NAME.matcher(name).replaceAll(".");
      // ... all repeating dots are replaced with one dot ...
      name = DOT_SEQUENCE.matcher(name).replaceAll(".");
      // ... and all leading and trailing dots are removed.
      name = StringUtil.trimLeading(StringUtil.trimTrailing(name, '.'), '.');

      // sanitize keywords and leading digits
      String[] parts = splitByDots(name);
      StringBuilder builder = new StringBuilder();
      boolean first = true;
      for (String part : parts) {
        if (part.length() == 0) {
          continue;
        }
        if (Character.isJavaIdentifierStart(part.charAt(0))) {
          if (!first) {
            builder.append('.');
          }
          builder.append(part);
          if (JavaLexer.isKeyword(part, LanguageLevel.JDK_1_9)) {
            builder.append('x');
          }
        }
        else { // it's a leading digit
          if (first) {
            builder.append("module");
          }
          builder.append(part);
        }
        first = false;
      }
      return builder.toString();
    }

    @NotNull
    static String[] splitByDots(@NotNull String text) {
      return SINGLE_DOT.split(text);
    }
  }

  private static class UniqueModuleNames {
    private final UniqueNameGenerator myNameGenerator;

    public UniqueModuleNames(@NotNull Project project) {
      LOG.assertTrue(!DumbService.isDumb(project), "Module name index should be ready");

      JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
      GlobalSearchScope scope = ProjectScope.getAllScope(project);

      List<PsiJavaModule> modules = new ArrayList<>();
      for (String key : index.getAllKeys(project)) {
        modules.addAll(index.get(key, project, scope));
      }
      myNameGenerator = new UniqueNameGenerator(modules, module -> ReadAction.compute(() -> module.getName()));
    }

    @NotNull
    public String getUniqueName(@NotNull Module module) {
      String name = NameConverter.convertModuleName(module.getName());
      return myNameGenerator.generateUniqueName(name);
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

  private static class ModuleInfo {
    final PsiDirectory myRootDir;
    final String myName;
    final List<String> myRequires;
    final List<String> myExports;

    private ModuleInfo(@NotNull PsiDirectory rootDir,
                       @NotNull String name,
                       @NotNull List<String> requires,
                       @NotNull List<String> exports) {
      myRootDir = rootDir;
      myName = name;
      myRequires = requires;
      myExports = exports;
    }

    boolean fileAlreadyExists() {
      return StreamEx.of(myRootDir.getChildren())
                     .select(PsiFile.class)
                     .map(PsiFileSystemItem::getName)
                     .anyMatch(MODULE_INFO_FILE::equals);
    }

    @NotNull
    CharSequence createModuleText() {
      StringBuilder text = new StringBuilder();
      text.append("module ").append(myName).append(" {");
      for (String dependency : myRequires) {
        if ("java.base".equals(dependency)) {
          continue;
        }
        boolean isBadSyntax = StreamEx.of(NameConverter.splitByDots(dependency))
                                      .anyMatch(part -> JavaLexer.isKeyword(part, LanguageLevel.JDK_1_9));
        text.append('\n').append(isBadSyntax ? "// " : " ").append(PsiKeyword.REQUIRES).append(' ').append(dependency).append(";");
      }
      if (!myRequires.isEmpty() && !myExports.isEmpty()) {
        text.append('\n');
      }
      for (String packageName : myExports) {
        text.append("\n ").append(PsiKeyword.EXPORTS).append(' ').append(packageName).append(";");
      }
      text.append("\n}");
      return text;
    }
  }
}
