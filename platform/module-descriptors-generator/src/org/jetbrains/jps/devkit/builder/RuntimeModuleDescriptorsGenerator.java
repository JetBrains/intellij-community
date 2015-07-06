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
import com.intellij.util.Processor;
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
import org.jetbrains.jps.model.serialization.JpsSerializationManager;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.platform.loader.impl.ModuleDescriptorsGenerationRunner;
import org.jetbrains.platform.loader.impl.repository.RepositoryConstants;
import org.jetbrains.platform.loader.repository.RuntimeModuleDescriptor;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.security.MessageDigest;
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

  public void generateForProductionMode(File distRoot) {
    final List<IntellijJarInfo> jars = collectJarsFromDist(distRoot);
    Map<RuntimeModuleId, RuntimeModuleDescriptor> includedModules = new LinkedHashMap<RuntimeModuleId, RuntimeModuleDescriptor>();
    Map<RuntimeModuleId, String> usedLibraryNames = new LinkedHashMap<RuntimeModuleId, String>();
    for (IntellijJarInfo jar : jars) {
      for (RuntimeModuleDescriptor descriptor : jar.getIncludedModules()) {
        includedModules.put(descriptor.getModuleId(), descriptor);
      }
      for (Map.Entry<RuntimeModuleId, RuntimeModuleId> entry : jar.getDependencies().entrySet()) {
        usedLibraryNames.put(entry.getKey(), "module '" + entry.getValue() + "' from JAR " + jar.getJarFile());
      }
    }
    for (RuntimeModuleDescriptor includedModule : includedModules.values()) {
      usedLibraryNames.remove(includedModule.getModuleId());
    }

    myMessageHandler.showProgressMessage(includedModules.size() + " modules detected in " + distRoot);
    List<JpsLibrary> allLibraries = new ArrayList<JpsLibrary>(myProject.getLibraryCollection().getLibraries());
    List<RuntimeModuleDescriptor> descriptorsToAdd = new ArrayList<RuntimeModuleDescriptor>();
    List<JpsRuntimeResourceRoot> runtimeResourceRoots = new ArrayList<JpsRuntimeResourceRoot>();
    for (JpsModule module : myProject.getModules()) {
      for (boolean test : BOOLEANS) {
        RuntimeModuleDescriptor descriptor = includedModules.remove(getRuntimeModuleName(module, test));
        if (descriptor != null) {
          JpsRuntimeResourceRootsCollection roots = JpsRuntimeResourcesService.getInstance().getRoots(module);
          if (roots != null) {
            runtimeResourceRoots.addAll(roots.getRoots());
          }
          descriptorsToAdd.add(descriptor);
        }
      }
      allLibraries.addAll(module.getLibraryCollection().getLibraries());
    }
    if (!includedModules.isEmpty()) {
      myMessageHandler.reportError("Unknown modules in the distribution: " + includedModules.keySet());
      return;
    }

    Set<JpsLibrary> usedLibraries = new LinkedHashSet<JpsLibrary>();
    for (JpsLibrary library : allLibraries) {
      if (usedLibraryNames.remove(getLibraryId(library)) != null) {
        usedLibraries.add(library);
      }
    }
    if (!usedLibraryNames.isEmpty()) {
      boolean hasErrors = false;
      for (Map.Entry<RuntimeModuleId, String> entry : usedLibraryNames.entrySet()) {
        if (entry.getKey().getStringId().startsWith(RuntimeModuleId.LIB_NAME_PREFIX)) {
          myMessageHandler.reportError("Unknown dependency in the distribution: '" + entry.getKey() + "' used by " + entry.getValue());
          hasErrors = true;
        }
      }
      if (hasErrors) {
        return;
      }
    }

    descriptorsToAdd.addAll(detectActualLocations(usedLibraries, jars));
    descriptorsToAdd.addAll(detectActualLocations(runtimeResourceRoots, distRoot));
    generateDescriptorsZip(new File(distRoot, RepositoryConstants.MODULE_DESCRIPTORS_DIR_NAME), descriptorsToAdd);
  }

  private List<RuntimeModuleDescriptor> detectActualLocations(List<JpsRuntimeResourceRoot> roots, File distRoot){
    List<RuntimeModuleDescriptor> result = new ArrayList<RuntimeModuleDescriptor>();
    try {
      String[] jarDirs = {"lib", "plugins"};
      Map<Bytes, File> actualLocations = new HashMap<Bytes, File>();
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      byte[] buffer = new byte[16 * 1024];
      for (String dir : jarDirs) {
        calcDigestRecursively(md5, buffer, new File(distRoot, dir), actualLocations);
      }
      for (JpsRuntimeResourceRoot root : roots) {
        File file = JpsPathUtil.urlToFile(root.getUrl());
        if (!file.exists()) {
          myMessageHandler.reportError("Runtime resource '" + root.getName() + "' refers to non-exitent file " + file.getAbsolutePath());
          continue;
        }
        File actualFile = actualLocations.get(new Bytes(calcDigestRecursively(md5, buffer, file, new HashMap<Bytes, File>())));
        if (actualFile == null) {
          myMessageHandler.reportError("Cannot find actual location for " + file.getAbsolutePath() + " from '" + root.getName() + "' runtime resource");
        }
        else {
          result.add(new LibraryDescriptor(RuntimeModuleId.moduleResource(root.getModule().getName(), root.getName()), Collections.singletonList(actualFile)));
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  private static class Bytes {
    final byte[] myBytes;

    public Bytes(byte[] bytes) {
      myBytes = bytes;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      return Arrays.equals(myBytes, ((Bytes)o).myBytes);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myBytes);
    }
  }

  private List<RuntimeModuleDescriptor> detectActualLocations(Set<JpsLibrary> libraries, List<IntellijJarInfo> jars) {
    List<RuntimeModuleDescriptor> descriptors = new ArrayList<RuntimeModuleDescriptor>();
    Map<Bytes, File> actualLocations = new HashMap<Bytes, File>();
    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      byte[] buffer = new byte[16 * 1024];
      for (IntellijJarInfo jar : jars) {
        if (jar.getIncludedModules().isEmpty()) {
          Bytes bytes = new Bytes(calcDigest(md5, buffer, jar.getJarFile()));
          if (actualLocations.containsKey(bytes)) {
            myMessageHandler.reportError("Duplicated jar files: " + actualLocations.get(bytes) + " and " + jar.getJarFile());
          }
          actualLocations.put(bytes, jar.getJarFile());
        }
      }

      for (JpsLibrary library : libraries) {
        List<File> actualFiles = new ArrayList<File>();
        for (File file : library.getFiles(JpsOrderRootType.COMPILED)) {
          if (!file.exists()) {
            myMessageHandler.reportError("Cannot find '" + getLibraryId(library).getStringId() + "' library file: " + file.getAbsolutePath());
            continue;
          }
          Bytes bytes = new Bytes(calcDigest(md5, buffer, file));
          File actual = actualLocations.get(bytes);
          if (actual == null) {
            myMessageHandler.reportError(
              "Cannot find actual location for " + file.getAbsolutePath() + " from '" + getLibraryId(library).getStringId() + "' library");
          }
          else {
            actualFiles.add(actual);
          }
        }
        descriptors.add(new LibraryDescriptor(getLibraryId(library), actualFiles));
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    return descriptors;
  }

  private static byte[] calcDigestRecursively(MessageDigest md5, byte[] buffer, File file, Map<Bytes, File> locations) throws IOException {
    if (file.isFile()) {
      byte[] bytes = calcDigest(md5, buffer, file);
      locations.put(new Bytes(bytes), file);
      return bytes;
    }
    File[] children = file.listFiles();
    if (children != null) {
      List<byte[]> hashes = new ArrayList<byte[]>();
      for (File child : children) {
        if (child.getName().startsWith(".git")) continue;
        hashes.add(child.getName().getBytes("UTF-8"));
        hashes.add(calcDigestRecursively(md5, buffer, child, locations));
      }
      md5.reset();
      for (byte[] hash : hashes) {
        md5.update(hash);
      }
    }
    byte[] bytes = md5.digest();
    locations.put(new Bytes(bytes), file);
    return bytes;
  }

  private static byte[] calcDigest(MessageDigest md5, byte[] buffer, File file) throws IOException {
    InputStream input = new BufferedInputStream(new FileInputStream(file));
    try {
      updateDigestFromStream(md5, buffer, input);
      return md5.digest();
    }
    finally {
      input.close();
    }
  }

  private static void updateDigestFromStream(MessageDigest md5, byte[] buffer, InputStream input) throws IOException {
    int len;
    while ((len = input.read(buffer)) > 0) {
      md5.update(buffer, 0, len);
    }
  }

  @NotNull
  private static List<IntellijJarInfo> collectJarsFromDist(File distRoot) {
    String[] jarDirs = {"lib", "plugins"};
    final List<IntellijJarInfo> jars = new ArrayList<IntellijJarInfo>();
    for (String jarDir : jarDirs) {
      File jarRoot = new File(distRoot, jarDir);
      FileUtil.processFilesRecursively(jarRoot, new Processor<File>() {
        @Override
        public boolean process(File file) {
          if (FileUtilRt.extensionEquals(file.getName(), "jar")) {
            jars.add(new IntellijJarInfo(file));
          }
          return true;
        }
      });
    }
    return jars;
  }

  public void generateForDevelopmentMode() {
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
    generateDescriptorsZip(outputDir, collectUsedLibrariesAndRuntimeResources());

    try {
      FileUtil.writeToFile(new File(outputDir, RepositoryConstants.VERSION_FILE_NAME), String.valueOf(RepositoryConstants.VERSION_NUMBER));
      FileUtil.writeToFile(new File(outputDir, RepositoryConstants.PROJECT_CONFIGURATION_HASH_FILE_NAME), RepositoryConstants.GENERATED_BY_COMPILER_HASH_MARK);
    }
    catch (IOException e) {
      myMessageHandler.reportError("Failed to write version file: " + e.getMessage());
    }
  }


  private void generateDescriptorsForModules() {
    myMessageHandler.showProgressMessage("Generating runtime dependencies information...");
    long start = System.currentTimeMillis();
    for (JpsModule module : myProject.getModules()) {
      for (final boolean test : BOOLEANS) {
        File outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(module, test);
        if (outputDirectory == null) {
          myMessageHandler.reportError((test ? "Test output" : "Output") + " directory isn't specified for '" +
                                       getRuntimeModuleName(module, test) + "' module");
          continue;
        }

        File outputFile = new File(outputDirectory, RepositoryConstants.getModuleDescriptorRelativePath(getRuntimeModuleName(module, test)));
        if (!FileUtilRt.createParentDirs(outputFile)) {
          myMessageHandler.reportError("Failed to create output directory '" + outputFile.getParent() + "'");
          continue;
        }
        try {
          final PrintWriter output = new PrintWriter(new FileWriter(outputFile));
          try {
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
            generateModuleXml(output, getRuntimeModuleName(module, test), dependencies, Collections.singletonList("../"));
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
  private static RuntimeModuleId getRuntimeModuleName(JpsModule module, boolean tests) {
    String moduleName = module.getName();
    return tests ? RuntimeModuleId.moduleTests(moduleName) : RuntimeModuleId.module(moduleName);
  }

  @NotNull
  private static JpsJavaDependenciesEnumerator enumerateRuntimeDependencies(JpsModule module) {
    return JpsJavaExtensionService.dependencies(module).withoutSdk().withoutModuleSourceEntries().runtimeOnly();
  }

  private List<RuntimeModuleDescriptor> collectUsedLibrariesAndRuntimeResources() {
    List<RuntimeModuleDescriptor> descriptors = new ArrayList<RuntimeModuleDescriptor>();
    Set<JpsLibrary> libraries = new LinkedHashSet<JpsLibrary>();
    for (JpsModule module : myProject.getModules()) {
      libraries.addAll(enumerateRuntimeDependencies(module).getLibraries());

      JpsRuntimeResourceRootsCollection rootsCollection = JpsRuntimeResourcesService.getInstance().getRoots(module);
      if (rootsCollection != null) {
        for (JpsRuntimeResourceRoot root : rootsCollection.getRoots()) {
          RuntimeModuleId id = RuntimeModuleId.moduleResource(module.getName(), root.getName());
          descriptors.add(new LibraryDescriptor(id, Collections.singletonList(JpsPathUtil.urlToFile(root.getUrl()))));
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
      descriptors.add(new LibraryDescriptor(getLibraryId(library), library.getFiles(JpsOrderRootType.COMPILED)));
    }
    return descriptors;
  }

  private static void generateModuleXml(PrintWriter output, RuntimeModuleId moduleId, List<RuntimeModuleId> dependencies, List<String> roots)
    throws XMLStreamException {
    output.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    output.println("<module xmlns=\"urn:jboss:module:1.3\" name=\"" + moduleId.getStringId() + "\">");
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
        output.println("    <resource-root path=\"" + root +"\" />");
      }
      output.println("  </resources>");
    }
    output.println("</module>");
    output.flush();
  }

  private static RuntimeModuleId getLibraryId(JpsLibrary library) {
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

  private void generateDescriptorsZip(File outputDir, List<RuntimeModuleDescriptor> descriptors) {
    myMessageHandler.showProgressMessage("Generating " + RepositoryConstants.MODULES_ZIP_NAME + "...");
    long start = System.currentTimeMillis();
    File outputFile = new File(outputDir, RepositoryConstants.MODULES_ZIP_NAME);
    FileUtilRt.createParentDirs(outputFile);
    try {
      ZipOutputStream zipOutput = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
      try {
        for (RuntimeModuleDescriptor descriptor : descriptors) {
          String name = descriptor.getModuleId().getStringId();
          ZipEntry dirEntry = new ZipEntry(name + "/");
          dirEntry.setMethod(ZipEntry.STORED);
          dirEntry.setSize(0);
          dirEntry.setCrc(0);
          zipOutput.putNextEntry(dirEntry);
          zipOutput.closeEntry();

          zipOutput.putNextEntry(new ZipEntry(name + "/module.xml"));
          PrintWriter output = new PrintWriter(zipOutput);
          List<File> files = descriptor.getModuleRoots();
          List<String> paths = new ArrayList<String>(files.size());
          for (File root : files) {
            String relativePath = FileUtilRt.getRelativePath(outputDir, root);
            if (relativePath == null) {
              myMessageHandler.reportError("Cannot get relative path for '" + name + "' module file: " + root.getAbsolutePath());
              return;
            }
            paths.add(relativePath);
          }
          generateModuleXml(output, descriptor.getModuleId(), descriptor.getDependencies(), paths);
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

  @Nullable
  public static File getDescriptorsDirectory(JpsProject project) {
    String outputUrl = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).getOutputUrl();
    return outputUrl != null ? new File(JpsPathUtil.urlToFile(outputUrl), RepositoryConstants.MODULE_DESCRIPTORS_DIR_NAME) : null;
  }

  public interface MessageHandler {
    void showProgressMessage(String message);
    void reportError(String message);
  }

  public static void main(String[] args) throws IOException {
    generate(args[0], null, new File(args[1]));
    System.exit(0);
  }
  /**
   * Called via reflection from {@link ModuleDescriptorsGenerationRunner}
   */
  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "unused"})
  public static void generate(String projectPath, String optionsPath, File ideDist) throws IOException {
    long start = System.currentTimeMillis();
    JpsModel model = JpsSerializationManager.getInstance().loadModel(projectPath, optionsPath);
    System.out.println("Model loaded in " + (System.currentTimeMillis() - start) + "ms");

    MessageHandler messageHandler = new MessageHandler() {
      @Override
      public void showProgressMessage(String message) {
        System.out.println(message);
      }

      @Override
      public void reportError(String message) {
        System.err.println(message);
      }
    };
    RuntimeModuleDescriptorsGenerator generator = new RuntimeModuleDescriptorsGenerator(model.getProject(), messageHandler);
    if (ideDist == null) {
      generator.generateForDevelopmentMode();
    }
    else {
      generator.generateForProductionMode(ideDist);
    }
  }

  private static class LibraryDescriptor implements RuntimeModuleDescriptor {
    private final List<File> myFiles;
    private final RuntimeModuleId myModuleId;

    public LibraryDescriptor(RuntimeModuleId moduleId, List<File> files) {
      myModuleId = moduleId;
      myFiles = files;
    }

    @NotNull
    @Override
    public RuntimeModuleId getModuleId() {
      return myModuleId;
    }

    @NotNull
    @Override
    public List<RuntimeModuleId> getDependencies() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public List<File> getModuleRoots() {
      return myFiles;
    }

    @Nullable
    @Override
    public InputStream readFile(@NotNull String relativePath) throws IOException {
      return null;
    }
  }
}
