package com.intellij.openapi.deployment;

import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.compiler.impl.make.BuildInstructionBase;
import com.intellij.compiler.impl.make.BuildRecipeImpl;
import com.intellij.compiler.impl.make.JarAndCopyBuildInstructionImpl;
import com.intellij.compiler.impl.make.JavaeeModuleBuildInstructionImpl;
import com.intellij.compiler.impl.make.ModuleBuilder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.compiler.make.BuildInstructionVisitor;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.compiler.make.FileCopyInstruction;
import com.intellij.openapi.compiler.make.ManifestBuilder;
import com.intellij.openapi.compiler.make.ModuleBuildProperties;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author Alexey Kudravtsev
 */
public class DeploymentUtilImpl extends DeploymentUtil implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.deployment.MakeUtilImpl");
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public boolean addModuleOutputContents(@NotNull CompileContext context,
                                         @NotNull BuildRecipe items,
                                         @NotNull final Module sourceModule,
                                         Module targetModule,
                                         final String outputRelativePath,
                                         String possibleBaseOuputPath,
                                         @Nullable FileFilter fileFilter) {
    final File outputPath = getModuleOutputPath(sourceModule);

    String[] sourceRoots = getSourceRootUrlsInReadAction(sourceModule);
    boolean ok = true;
    if (outputPath != null && sourceRoots.length != 0) {
      ok = checkModuleOutputExists(outputPath, sourceModule, context);
      boolean added = addItemsRecursively(items, outputPath, targetModule, outputRelativePath, fileFilter, possibleBaseOuputPath);
      if (!added) {
        String additionalMessage = CompilerBundle.message("message.text.change.module.output.directory.or.module.exploded.directory",
                                                      ModuleUtil.getModuleNameInReadAction(sourceModule),
                                                      ModuleUtil.getModuleNameInReadAction(targetModule));
        reportRecursiveCopying(context, outputPath.getPath(), appendToPath(possibleBaseOuputPath,outputRelativePath), CompilerBundle.message(
          "module.output.directory", ModuleUtil.getModuleNameInReadAction(sourceModule)), additionalMessage);
        ok = false;
      }
    }
    return ok;
  }

  private static String[] getSourceRootUrlsInReadAction(final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        return ModuleRootManager.getInstance(module).getSourceRootUrls();
      }
    });
  }

  private static File getModuleOutputPath(final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<File>() {
      @Nullable
      public File compute() {
        final String url = ModuleRootManager.getInstance(module).getCompilerOutputPathUrl();
        if (url == null) return null;
        return new File(PathUtil.toPresentableUrl(url));
      }
    });
  }

  private static boolean checkModuleOutputExists(final File outputPath, final Module sourceModule, CompileContext context) {
    if (outputPath == null || !outputPath.exists()) {
      String moduleName = ModuleUtil.getModuleNameInReadAction(sourceModule);
      final String message = CompilerBundle.message("message.text.directory.not.found.please.recompile",
                                                outputPath == null ? moduleName : FileUtil.toSystemDependentName(outputPath.getPath()),
                                                moduleName);
      context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      return false;
    }
    return true;
  }

  public void addLibraryLink(@NotNull final CompileContext context,
                             @NotNull final BuildRecipe items,
                             @NotNull final LibraryLink libraryLink,
                             @NotNull final Module module,
                             final String possibleBaseOutputPath) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        String outputRelativePath;
        final PackagingMethod packagingMethod = libraryLink.getPackagingMethod();
        if (packagingMethod.equals(PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST)
            || packagingMethod.equals(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST)) {
          outputRelativePath = getRelativePathForManifestLinking(libraryLink.getURI());
        }
        else {
          outputRelativePath = libraryLink.getURI();
        }
        boolean isDestinationDirectory = libraryLink.getSingleFileName() == null;
        final List<String> urls = libraryLink.getUrls();
        for (String url : urls) {
          final String path = PathUtil.toPresentableUrl(url);
          final File file = new File(path);
          String fileDestination = isDestinationDirectory ? appendToPath(outputRelativePath, file.getName()) : outputRelativePath;
          if (file.isDirectory()) {
            boolean ok;
            if (packagingMethod.equals(PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST)
                || packagingMethod.equals(PackagingMethod.COPY_FILES)) {
              ok = addItemsRecursively(items, file, module, fileDestination, null, possibleBaseOutputPath);
            }
            else {
              if (!packagingMethod.equals(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST) &&
                  !packagingMethod.equals(PackagingMethod.JAR_AND_COPY_FILE)) {
                libraryLink.setPackagingMethod(PackagingMethod.JAR_AND_COPY_FILE);
                context.addMessage(CompilerMessageCategory.WARNING,
                                   CompilerBundle.message("message.text.packaging.method.for.library.reset", libraryLink.getPresentableName(),
                                                      PackagingMethod.JAR_AND_COPY_FILE),
                                   null, -1, -1);
              }
              BuildInstruction instruction = new JarAndCopyBuildInstructionImpl(module, file, fileDestination, null);
              items.addInstruction(instruction);
              ok = true;
            }
            if (!ok) {
              final String name = libraryLink.getPresentableName();
              String additionalMessage = CompilerBundle.message("message.text.adjust.library.path");
              reportRecursiveCopying(context, file.getPath(), fileDestination,
                                     CompilerBundle.message("directory.description.library.directory", name), additionalMessage);
            }
          }
          else {
            items.addFileCopyInstruction(file, false, module, fileDestination, null);
          }
        }
      }
    });
  }

  public void copyFile(@NotNull final File fromFile,
                       @NotNull final File toFile,
                       @NotNull CompileContext context,
                       @Nullable Set<String> writtenPaths,
                       @Nullable FileFilter fileFilter) throws IOException {
    if (fileFilter != null && !fileFilter.accept(fromFile)) {
      return;
    }
    checkPathDoNotNavigatesUpFromFile(fromFile);
    checkPathDoNotNavigatesUpFromFile(toFile);
    if (fromFile.isDirectory()) {
      final File[] fromFiles = fromFile.listFiles();
      toFile.mkdirs();
      for (File file : fromFiles) {
        copyFile(file, new File(toFile, file.getName()), context, writtenPaths, fileFilter);
      }
      return;
    }
    if (toFile.isDirectory()) {
      context.addMessage(CompilerMessageCategory.ERROR,
                         CompilerBundle.message("message.text.destination.is.directory", createCopyErrorMessage(fromFile, toFile)), null, -1, -1);
      return;
    }
    if (fromFile.equals(toFile)
        || writtenPaths != null && !writtenPaths.add(toFile.getPath())) {
      return;
    }
    if (!FileUtil.isFilePathAcceptable(toFile, fileFilter)) return;
    if (context.getProgressIndicator() != null) {
      context.getProgressIndicator().setText(CompilerBundle.message("progress.text.copying.file", fromFile.getPath()));
    }
    try {
      LOG.debug("Copy file '" + fromFile + "' to '"+toFile+"'");
      FileUtil.copy(fromFile, toFile);
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, createCopyErrorMessage(fromFile, toFile) + ": "+e.getLocalizedMessage(), null, -1, -1);
    }
  }

  // OS X is sensitive for that
  private static void checkPathDoNotNavigatesUpFromFile(File file) {
    String path = file.getPath();
    int i = path.indexOf("..");
    if (i != -1) {
      String filepath = path.substring(0,i-1);
      File filepart = new File(filepath);
      if (filepart.exists() && !filepart.isDirectory()) {
        LOG.error("Incorrect file path: '" + path + '\'');
      }
    }
  }

  private static String createCopyErrorMessage(final File fromFile, final File toFile) {
    return CompilerBundle.message("message.text.error.copying.file.to.file", FileUtil.toSystemDependentName(fromFile.getPath()),
                              FileUtil.toSystemDependentName(toFile.getPath()));
  }

  public final boolean addItemsRecursively(@NotNull BuildRecipe items,
                                           @NotNull File root,
                                           @NotNull Module module,
                                           String outputRelativePath,
                                           @Nullable FileFilter fileFilter,
                                           String possibleBaseOutputPath) {
    if (outputRelativePath == null) outputRelativePath = "";
    outputRelativePath = trimForwardSlashes(outputRelativePath);

    if (possibleBaseOutputPath != null) {
      File file = new File(possibleBaseOutputPath, outputRelativePath);
      String relativePath = getRelativePath(root, file);
      if (relativePath != null && !relativePath.startsWith("..") && relativePath.length() != 0) {
        return false;
      }
    }

    items.addFileCopyInstruction(root, true, module, outputRelativePath, fileFilter);
    return true;
  }

  public Map<Module, BuildRecipe> computeModuleBuildInstructionMap(@NotNull final Module[] affectedModules, @NotNull final CompileContext context) {
    // should obtain keys in the insertion order
    final Map<Module, BuildRecipe> moduleItemsMap = new LinkedHashMap<Module, BuildRecipe>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        // affectedModules modules sorted by dependency
        for (Module module : affectedModules) {
          ModuleBuildProperties moduleBuildProperties = ModuleBuildProperties.getInstance(module);
          if (moduleBuildProperties == null) continue;

          final BuildRecipe buildRecipe = ModuleBuilder.getInstance(module).getModuleBuildInstructions(context);
          moduleItemsMap.put(module, buildRecipe);
        }
      }
    });
    return moduleItemsMap;
  }

  public void reportDeploymentDescriptorDoesNotExists(DeploymentItem descriptor, CompileContext context, Module module) {
    final String description = module.getModuleType().getName() + " '"+module.getName()+'\'';
    String descriptorPath = VfsUtil.urlToPath(descriptor.getUrl());
    context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message(
      "message.text.compiling.item.deployment.descriptor.could.not.be.found", description, descriptorPath), null, -1, -1);
  }

  public void addJ2EEModuleOutput(@NotNull BuildRecipe buildRecipe,
                                  @NotNull ModuleBuildProperties moduleBuildProperties,
                                  final String relativePath) {
    buildRecipe.addInstruction(new JavaeeModuleBuildInstructionImpl(moduleBuildProperties, relativePath));
  }

  public static boolean containsExternalDependencyInstruction(@NotNull ModuleContainer moduleProperties) {
    final ContainerElement[] elements = moduleProperties.getElements();
    for (ContainerElement element : elements) {
      if (element.getPackagingMethod() == PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST
          || element.getPackagingMethod() == PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST) {
        return true;
      }
    }
    return false;
  }

  private static String getRelativePathForManifestLinking(String relativePath) {
    if (!StringUtil.startsWithChar(relativePath, '/')) relativePath = '/' + relativePath;
    relativePath = ".." + relativePath;
    return relativePath;
  }

  public @Nullable File findUserSuppliedManifestFile(@NotNull BuildRecipe buildRecipe) {
    final Ref<File> ref = Ref.create(null);
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws Exception {
        final File file = instruction.findFileByRelativePath(JarFile.MANIFEST_NAME);
        ref.set(file);
        return file == null;
      }
    }, false);
    return ref.get();
  }

  public @Nullable Manifest createManifest(@NotNull BuildRecipe buildRecipe) {
    if (findUserSuppliedManifestFile(buildRecipe) != null) {
      return null;
    }

    final StringBuffer classPath = new StringBuffer();

    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
        final String outputRelativePath = instruction.getOutputRelativePath();
        if (instruction.isExternalDependencyInstruction()) {
          if (classPath.length() != 0) classPath.append(' ');
          final String jarReference = PathUtil.getCanonicalPath("/tmp/" + outputRelativePath).substring(1);
          classPath.append(trimForwardSlashes(jarReference));
        }
        return true;
      }
    }, false);
    final Manifest manifest = new Manifest();
    Attributes mainAttributes = manifest.getMainAttributes();
    if (classPath.length() > 0) {
      mainAttributes.put(Attributes.Name.CLASS_PATH, classPath.toString());
    }
    ManifestBuilder.setGlobalAttributes(mainAttributes);
    return manifest;
  }

  private static boolean precedesInOrder(final Module parentModule, final String module1Name, final String module2Name) {
    Boolean found = ModuleRootManager.getInstance(parentModule).processOrder(new RootPolicy<Boolean>() {
      @Nullable
      public Boolean visitModuleOrderEntry(final ModuleOrderEntry moduleOrderEntry, final Boolean found) {
        if (found == null) {
          String moduleName = moduleOrderEntry.getModuleName();
          if (Comparing.strEqual(module1Name, moduleName)) return Boolean.TRUE;
          if (Comparing.strEqual(module2Name, moduleName)) return Boolean.FALSE;
        }
        return found;
      }
    }, null);
    return found != null && found.booleanValue();
  }

  public void addDependentModules(@NotNull ModuleLink[] containingModules,
                                  final ModuleType moduleType,
                                  final BuildRecipe instructions,
                                  CompileContext context) {
    if (containingModules.length == 0) return;
    final Module parentModule = containingModules[0].getParentModule();
    Arrays.sort(containingModules, new Comparator<ModuleLink>() {
      public int compare(final ModuleLink o1, final ModuleLink o2) {
        return precedesInOrder(parentModule, o1.getName(), o2.getName()) ? 1 : -1;
      }
    });
    for (ModuleLink moduleLink : containingModules) {
      if (moduleLink.getParentModule() != parentModule) {
        LOG.error("Expected: " + ModuleUtil.getModuleNameInReadAction(parentModule) +
                  "; was:" +
                  ModuleUtil.getModuleNameInReadAction(moduleLink.getParentModule()));
      }
      Module module = moduleLink.getModule();
      if (module != null && moduleType.equals(module.getModuleType())) {
        final BuildRecipe childBuildRecipe = ModuleBuilder.getInstance(module).getModuleBuildInstructions(context);
        childBuildRecipe.visitInstructions(new BuildInstructionVisitor() {
          public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
            final BuildInstructionBase cloned = ((BuildInstructionBase)instruction).clone();
            instructions.addInstruction(cloned);
            return true;
          }
        }, false);
      }
    }
  }

  public void addJavaModuleOutputs(@NotNull final Module module,
                                   @NotNull ModuleLink[] containingModules,
                                   @NotNull BuildRecipe instructions,
                                   @NotNull CompileContext context,
                                   String explodedPath) {
    for (ModuleLink moduleLink : containingModules) {
      Module childModule = moduleLink.getModule();
      if (childModule != null && ModuleType.JAVA.equals(childModule.getModuleType())) {
        final PackagingMethod packagingMethod = moduleLink.getPackagingMethod();
        if (PackagingMethod.DO_NOT_PACKAGE.equals(packagingMethod)) {
          continue;
        }
        if (PackagingMethod.JAR_AND_COPY_FILE.equals(packagingMethod)) {
          addJarJavaModuleOutput(instructions, childModule, moduleLink.getURI(), context);
        }
        else if (PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST.equals(packagingMethod)) {
          String relativePath = getRelativePathForManifestLinking(moduleLink.getURI());
          addJarJavaModuleOutput(instructions, childModule, relativePath, context);
        }
        else if (PackagingMethod.COPY_FILES.equals(packagingMethod)) {
          addModuleOutputContents(context, instructions, childModule, module, moduleLink.getURI(), explodedPath,
                                  null);
        }
        else if (PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST.equals(packagingMethod)) {
          moduleLink.setPackagingMethod(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST);
          String relativePath = getRelativePathForManifestLinking(moduleLink.getURI());
          addJarJavaModuleOutput(instructions, childModule, relativePath, context);
          context.addMessage(CompilerMessageCategory.WARNING,
                             CompilerBundle.message("message.text.packaging.method.for.module.reset.to.method",
                                                ModuleUtil.getModuleNameInReadAction(childModule),
                                                PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST),
                             null, -1, -1);
        }
        else {
          LOG.error("invalid method " + packagingMethod);
        }
      }
    }
  }

  private static void addJarJavaModuleOutput(BuildRecipe instructions,
                                             Module module,
                                             String relativePath,
                                             CompileContext context) {
    final String[] sourceUrls = getSourceRootUrlsInReadAction(module);
    if (sourceUrls.length > 0) {
      final File outputPath = getModuleOutputPath(module);
      checkModuleOutputExists(outputPath, module, context);
      if (outputPath != null) {
        instructions.addInstruction(new JarAndCopyBuildInstructionImpl(module, outputPath, relativePath, null));
      }
    }
  }

  public BuildRecipe getModuleItems(@NotNull final Module module) {
    return ModuleBuilder.getInstance(module).getModuleBuildInstructions(DummyCompileContext.getInstance());
  }

  public ModuleLink createModuleLink(Module dep, Module module) {
    return new ModuleLinkImpl(dep, module);
  }

  public LibraryLink createLibraryLink(Library library, @NotNull Module parentModule) {
    return new LibraryLinkImpl(library, parentModule);
  }

  public ModuleContainer createModuleContainer(@NotNull Module module) {
    return new ModuleContainerImpl(module);
  }

  public BuildRecipe createBuildRecipe() {
    return new BuildRecipeImpl();
  }

  @Nullable
  public CompileScope getOutOfSourceJ2eeCompileScope(@NotNull CompileScope compileScope) {
    Module[] containingModules = getContainingModules(compileScope);
    final Collection<VirtualFile> j2eeSpecificFiles = new THashSet<VirtualFile>();
    final Collection<Module> affectedModules = new THashSet<Module>();
    for (final Module module : containingModules) {
      ModuleBuildProperties moduleBuildProperties = ModuleBuildProperties.getInstance(module);
      if (moduleBuildProperties == null || !moduleBuildProperties.willBuildExploded()) {
        continue;
      }
      final ModuleCompileScope moduleCompileScope = new ModuleCompileScope(module, true);
      BuildRecipe buildRecipe = DeploymentUtil.getInstance().getModuleItems(module);
      ModuleBuilder.getInstance(module).clearBuildRecipeCaches();
      buildRecipe.visitInstructions(new BuildInstructionVisitor() {
        public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
          VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(instruction.getFile());
          if (virtualFile != null && !moduleCompileScope.belongs(virtualFile.getUrl())) {
            j2eeSpecificFiles.add(virtualFile);
            affectedModules.add(module);
          }
          return true;
        }
      }, false);
    }
    if (j2eeSpecificFiles.size() == 0) {
      return null;
    }
    VirtualFile[] virtualFiles = j2eeSpecificFiles.toArray(new VirtualFile[j2eeSpecificFiles.size()]);
    Module[] affectedModuleArray = affectedModules.toArray(new Module[affectedModules.size()]);
    return new FileSetCompileScope(virtualFiles, affectedModuleArray);
  }

  @Nullable
  public ContainerElement createElementByOrderEntry(OrderEntry orderEntry, Module module) {
    if (orderEntry instanceof ModuleOrderEntry) {
      if (((ModuleOrderEntry)orderEntry).getModule() != null) {
        return new ModuleLinkImpl(((ModuleOrderEntry)orderEntry).getModule(), module);
      }
    }
    else if (orderEntry instanceof LibraryOrderEntry) {
      LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
      Library library = libraryOrderEntry.getLibrary();
      if (library != null && library.getUrls(OrderRootType.CLASSES).length != 0) {
        return new LibraryLinkImpl(library, module);
      }
    }
    return null;
  }

  public @Nullable ContainerElement findElementByOrderEntry(ModuleContainer container, OrderEntry entry) {
    if (entry instanceof ModuleOrderEntry) {
      final Module module = ((ModuleOrderEntry)entry).getModule();
      if (module == null) return null;

      for (ModuleLink link : container.getContainingModules()) {
        if (link.getModule() == module) {
          return link;
        }
      }
    }
    else if (entry instanceof LibraryOrderEntry) {
      final Library library = ((LibraryOrderEntry)entry).getLibrary();
      if (library == null) return null;

      for (LibraryLink link : container.getContainingLibraries()) {
        if (OrderEntryUtil.equals(library, link.getLibrary())) {
          return link;
        }
      }
    }
    return null;
  }

  // returns modules entirely contained in this compileScope
  private static Module[] getContainingModules(CompileScope compileScope) {
    if (compileScope instanceof ProjectCompileScope || compileScope instanceof ModuleCompileScope) {
      return compileScope.getAffectedModules();
    }
    return Module.EMPTY_ARRAY;
  }

  public static String getOrCreateExplodedDir(@NotNull ModuleBuildProperties moduleBuildProperties) {
    if (moduleBuildProperties.isExplodedEnabled()) {
      return moduleBuildProperties.getExplodedPath();
    }

    final Module module = moduleBuildProperties.getModule();
    final PropertiesComponent properties = PropertiesComponent.getInstance(module.getProject());

    try {
      @NonNls final String name = "TEMP_MODULE_EXPLODED_DIR_FOR_" + ModuleUtil.getModuleNameInReadAction(module);
      String dir = properties.getValue(name);
      if (dir == null) {
        File path = FileUtil.createTempDirectory("webExplodedDir", "tmp");
        dir = path.getAbsolutePath();
        properties.setValue(name, dir);
      }
      return dir;
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  public static String getDirectoryToBuildExploded(ModuleBuildProperties moduleBuildProperties) {
    if (moduleBuildProperties.willBuildExploded()) {
      return getOrCreateExplodedDir(moduleBuildProperties);
    }
    return null;
  }

  public static BuildParticipant[] getBuildParticipants(Module module) {
    BuildParticipant[] participants = module.getComponents(BuildParticipant.class);

    final ModuleBuildProperties properties = ModuleBuildProperties.getInstance(module);
    if (properties != null) {
      final BuildParticipant participant = properties.getBuildParticipant();
      if (participant != null) {
        participants = ArrayUtil.append(participants, participant);
      }
    }

    return participants;
  }
}
