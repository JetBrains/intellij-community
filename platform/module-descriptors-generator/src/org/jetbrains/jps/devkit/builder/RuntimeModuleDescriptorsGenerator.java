/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.devkit.builder;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.devkit.model.JpsRuntimeResourceRoot;
import org.jetbrains.jps.devkit.model.JpsRuntimeResourceRootsCollection;
import org.jetbrains.jps.devkit.model.JpsRuntimeResourcesService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsSerializationManager;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.platform.loader.impl.ModuleDescriptorsGenerationRunner;
import org.jetbrains.platform.loader.impl.repository.RepositoryConstants;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author nik
 */
public class RuntimeModuleDescriptorsGenerator {
  private static final boolean[] BOOLEANS = {false, true};
  private final JpsProject myProject;
  private final MessageHandler myMessageHandler;

  public RuntimeModuleDescriptorsGenerator(@NotNull JpsProject project, @NotNull MessageHandler messageHandler) {
    myProject = project;
    myMessageHandler = messageHandler;
  }

  public void generateForProductionMode(File distRoot, final List<RuntimeModuleDescriptorData> descriptors) {
    myMessageHandler.showProgressMessage("Generating runtime module descriptors for packaged distribution at '" + distRoot.getAbsolutePath() + "'");
    File descriptorsOutputFile = new File(distRoot, RepositoryConstants.MODULE_DESCRIPTORS_DIR_NAME + "/" + RepositoryConstants.MODULES_ZIP_NAME);
    generateDescriptorsZip(descriptorsOutputFile, descriptors);
  }

  @NotNull
  private RuntimeModuleDescriptorData createDescriptor(RuntimeModuleId moduleId, List<File> files, File descriptorsOutput) {
    return new RuntimeModuleDescriptorData(moduleId, getRelativePaths(files, descriptorsOutput));
  }

  public void generateForDevelopmentMode() {
    myMessageHandler.showProgressMessage("Generating runtime module descriptors for source distribution at '" +
                                         JpsModelSerializationDataService.getBaseDirectory(myProject) + "'");
    File outputDir = getDescriptorsDirectory(myProject);
    if (outputDir == null) {
      myMessageHandler.reportError("Project compiler output directory is not specified");
      return;
    }
    if (!FileUtilRt.createDirectory(outputDir)) {
      myMessageHandler.reportError("Failed to create output directory " + outputDir);
      return;
    }

    generateDescriptorsForModules();
    generateDescriptorsZip(new File(outputDir, RepositoryConstants.MODULES_ZIP_NAME),
                           collectUsedLibrariesAndRuntimeResources(new File(outputDir, RepositoryConstants.MODULES_ZIP_NAME))
    );

    try {
      FileUtil.writeToFile(new File(outputDir, RepositoryConstants.VERSION_FILE_NAME), String.valueOf(RepositoryConstants.VERSION_NUMBER));
      FileUtil.writeToFile(new File(outputDir, RepositoryConstants.PROJECT_CONFIGURATION_HASH_FILE_NAME),
                           RepositoryConstants.GENERATED_BY_COMPILER_HASH_MARK);
    }
    catch (IOException e) {
      myMessageHandler.reportError("Failed to write version file: " + e.getMessage());
    }
  }


  public void generateDescriptorsForModules() {
    myMessageHandler.showProgressMessage("Generating descriptors in module output directories...");
    long start = System.currentTimeMillis();
    for (JpsModule module : myProject.getModules()) {
      for (final boolean test : BOOLEANS) {
        File outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(module, test);
        if (outputDirectory == null) {
          myMessageHandler.reportError((test ? "Test output" : "Output") + " directory isn't specified for '" +
                                       getRuntimeModuleName(module, test) + "' module");
          continue;
        }

        File outputFile =
          new File(outputDirectory, RepositoryConstants.getModuleDescriptorRelativePath(getRuntimeModuleName(module, test)));
        if (!FileUtilRt.createParentDirs(outputFile)) {
          myMessageHandler.reportError("Failed to create output directory '" + outputFile.getParent() + "'");
          continue;
        }
        try {
          final PrintWriter output = new PrintWriter(new FileWriter(outputFile));
          try {
            generateModuleXml(output, createModuleDescriptor(module, test, Collections.singletonList("../")));
          }
          finally {
            output.close();
          }
        }
        catch (Exception e) {
          myMessageHandler.reportError("Failed to write dependencies for '" + module.getName() + "' module" + (test ? " tests" : ""));
        }
      }
    }
    myMessageHandler.showProgressMessage("Descriptors for " + myProject.getModules().size() + " modules generated in " +
                                         (System.currentTimeMillis() - start) + "ms");
  }

  @NotNull
  public static RuntimeModuleDescriptorData createModuleDescriptor(JpsModule module, final boolean test, final List<String> moduleRoots) {
    final List<RuntimeModuleId> dependencies = new ArrayList<RuntimeModuleId>();
    JpsJavaDependenciesEnumerator enumerator = enumerateRuntimeDependencies(module);
    if (!test) {
      enumerator.productionOnly();
    }
    enumerator.processModuleAndLibraries(
      new Consumer<JpsModule>() {
        @Override
        public void consume(JpsModule module) {
          dependencies.add(getRuntimeModuleName(module, test));
        }
      },
      new Consumer<JpsLibrary>() {
        @Override
        public void consume(JpsLibrary library) {
          dependencies.add(getLibraryId(library));
        }
      }
    );
    return new RuntimeModuleDescriptorData(getRuntimeModuleName(module, test), moduleRoots, dependencies);
  }

  @NotNull
  private static RuntimeModuleId getRuntimeModuleName(JpsModule module, boolean tests) {
    String moduleName = module.getName();
    return tests ? RuntimeModuleId.moduleTests(moduleName) : RuntimeModuleId.module(moduleName);
  }

  @NotNull
  private static JpsJavaDependenciesEnumerator enumerateRuntimeDependencies(JpsModule module) {
    return JpsJavaExtensionService.dependencies(module).withoutSdk().withoutModuleSourceEntries().runtimeOnly();
  }

  private List<RuntimeModuleDescriptorData> collectUsedLibrariesAndRuntimeResources(File outputDir) {
    List<RuntimeModuleDescriptorData> descriptors = new ArrayList<RuntimeModuleDescriptorData>();
    Set<JpsLibrary> libraries = new LinkedHashSet<JpsLibrary>();
    for (JpsModule module : myProject.getModules()) {
      libraries.addAll(enumerateRuntimeDependencies(module).getLibraries());

      JpsRuntimeResourceRootsCollection rootsCollection = JpsRuntimeResourcesService.getInstance().getRoots(module);
      if (rootsCollection != null) {
        for (JpsRuntimeResourceRoot root : rootsCollection.getRoots()) {
          RuntimeModuleId id = RuntimeModuleId.moduleResource(module.getName(), root.getName());
          final List<File> files = Collections.singletonList(JpsPathUtil.urlToFile(root.getUrl()));
          descriptors.add(createDescriptor(id, files, outputDir));
        }
      }
    }
    Set<RuntimeModuleId> names = new HashSet<RuntimeModuleId>();
    for (JpsLibrary library : libraries) {
      if (!names.add(getLibraryId(library))) {
        myMessageHandler.reportError("Duplicated library id '" + getLibraryId(library) + "'");
      }
    }

    for (JpsLibrary library : libraries) {
      final RuntimeModuleId moduleId = getLibraryId(library);
      final List<File> files = library.getFiles(JpsOrderRootType.COMPILED);
      descriptors.add(createDescriptor(moduleId, files, outputDir));
    }
    return descriptors;
  }

  public static RuntimeModuleId getLibraryId(JpsLibrary library) {
    String name = library.getName();
    JpsElementBase element = ((JpsElementBase)library).getParent().getParent();
    if (element instanceof JpsModule) {
      List<File> files = library.getFiles(JpsOrderRootType.COMPILED);
      if (name.startsWith("#") && files.size() == 1) {
        name = files.get(0).getName();
      }
      return RuntimeModuleId.moduleLibrary(((JpsModule)element).getName(), name);
    }
    return RuntimeModuleId.projectLibrary(name);
  }

  private void generateDescriptorsZip(final File outputFile, List<RuntimeModuleDescriptorData> descriptors) {
    myMessageHandler.showProgressMessage("Generating " + RepositoryConstants.MODULES_ZIP_NAME + "...");
    long start = System.currentTimeMillis();
    FileUtil.delete(outputFile);
    FileUtilRt.createParentDirs(outputFile);
    try {
      ZipOutputStream zipOutput = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
      try {
        UniqueNameGenerator generator = new UniqueNameGenerator();
        for (RuntimeModuleDescriptorData descriptor : descriptors) {
          String name = descriptor.getModuleId().getStringId();
          String dirName = generator.generateUniqueName(name, "", "", ".dup.", "");
          ZipEntry dirEntry = new ZipEntry(dirName + "/");
          dirEntry.setMethod(ZipEntry.STORED);
          dirEntry.setSize(0);
          dirEntry.setCrc(0);
          zipOutput.putNextEntry(dirEntry);
          zipOutput.closeEntry();

          zipOutput.putNextEntry(new ZipEntry(dirName + "/module.xml"));
          PrintWriter output = new PrintWriter(zipOutput);
          generateModuleXml(output, descriptor);
          zipOutput.closeEntry();
        }
      }
      finally {
        zipOutput.close();
      }
    }
    catch (Exception e) {
      myMessageHandler.reportError("Failed to write " + outputFile + "(" + e.getMessage() + ")");
    }
    myMessageHandler.showProgressMessage("Descriptors for " + descriptors.size() + " modules generated in " +
                                         (System.currentTimeMillis() - start) + "ms");
  }

  private static void generateModuleXml(PrintWriter output, RuntimeModuleDescriptorData descriptor) throws XMLStreamException {
    List<RuntimeModuleId> dependencies = descriptor.getDependencies();
    List<String> roots = descriptor.getModuleRoots();
    output.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    output.println("<module xmlns=\"urn:jboss:module:1.3\" name=\"" + descriptor.getModuleId().getStringId() + "\">");
    if (!dependencies.isEmpty()) {
      output.println("  <dependencies>");
      for (RuntimeModuleId dependency : dependencies) {
        output.println("    <module name=\"" + dependency.getStringId() + "\" />");
      }
      output.println("  </dependencies>");
    }
    if (!roots.isEmpty()) {
      output.println("  <resources>");
      for (String root : roots) {
        output.println("    <resource-root path=\"" + root + "\" />");
      }
      output.println("  </resources>");
    }
    output.println("</module>");
    output.flush();
  }

  @Nullable
  private List<String> getRelativePaths(List<File> files, File descriptorsOutput) {
    List<String> paths = new ArrayList<String>(files.size());
    for (File root : files) {
      String relativePath = FileUtilRt.getRelativePath(descriptorsOutput.getAbsolutePath(), root.getAbsolutePath(), '/');
      if (relativePath == null) {
        myMessageHandler.reportError("Cannot get relative path for " + root.getAbsolutePath());
      }
      paths.add(relativePath);
    }
    return paths;
  }

  @Nullable
  public static File getDescriptorsDirectory(JpsProject project) {
    String outputUrl = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).getOutputUrl();
    return outputUrl != null ? new File(JpsPathUtil.urlToFile(outputUrl), RepositoryConstants.MODULE_DESCRIPTORS_DIR_NAME) : null;
  }

  public interface MessageHandler {
    void showProgressMessage(String message);

    void reportError(String message);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static final MessageHandler SYSTEM_OUT_HANDLER = new MessageHandler() {
    @Override
    public void showProgressMessage(String message) {
      System.out.println(message);
    }

    @Override
    public void reportError(String message) {
      System.err.println(message);
    }
  };

  public static void main(String[] args) throws IOException {
    if ("--in-module-outputs".equals(args[0])) {
      JpsModel model = JpsSerializationManager.getInstance().loadModel(args[1], null);
      new RuntimeModuleDescriptorsGenerator(model.getProject(), SYSTEM_OUT_HANDLER).generateDescriptorsForModules();
    }
    else {
      generate(args[0], null);
    }
    System.exit(0);
  }

  /**
   * Called via reflection from {@link ModuleDescriptorsGenerationRunner}
   */
  @SuppressWarnings("unused")
  public static void generate(String projectPath, String optionsPath) throws IOException {
    long start = System.currentTimeMillis();
    JpsModel model = JpsSerializationManager.getInstance().loadModel(projectPath, optionsPath);
    System.out.println("Model loaded in " + (System.currentTimeMillis() - start) + "ms");

    RuntimeModuleDescriptorsGenerator generator = new RuntimeModuleDescriptorsGenerator(model.getProject(), SYSTEM_OUT_HANDLER);
    generator.generateForDevelopmentMode();
  }
}
