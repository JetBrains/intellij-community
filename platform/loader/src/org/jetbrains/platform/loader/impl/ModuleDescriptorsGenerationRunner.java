package org.jetbrains.platform.loader.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.PlatformLoaderException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.platform.loader.impl.repository.RepositoryConstants.*;

/**
 * @author nik
 */
public class ModuleDescriptorsGenerationRunner {
  private final File myIdeaProjectHome;
  private final File myOutputDir;
  private String myProjectConfigurationHash;
  private File myIdeDist;

  public ModuleDescriptorsGenerationRunner(File ideaProjectHome, File outputDir, File ideDist) {
    myIdeaProjectHome = ideaProjectHome;
    myOutputDir = outputDir;
    myIdeDist = ideDist;
  }

  public static void main(String[] args) {
    final File ideaProjectHome = new File("").getAbsoluteFile().getParentFile();
    File outputDir = new File(ideaProjectHome, "out/classes");
    File ideDist = args.length > 0 ? new File(args[0]) : null;
    runGenerator(ideaProjectHome, outputDir, ideDist);
    System.exit(0);
  }

  public static void runGenerator(File ideaProjectHome, File outputDir, File ideDist) {
    ModuleDescriptorsGenerationRunner runner = new ModuleDescriptorsGenerationRunner(ideaProjectHome, outputDir, ideDist);
    if (!runner.isUpToDate()) {
      runner.runGenerator();
    }
  }

  private boolean isUpToDate() {
    try {
      File versionFile = new File(myOutputDir, MODULE_DESCRIPTORS_DIR_NAME + "/" + VERSION_FILE_NAME);
      String version = FileUtilRt.loadFile(versionFile);
      if (!String.valueOf(VERSION_NUMBER).equals(version)) {
        return false;
      }
      String storedHash = FileUtilRt.loadFile(getProjectConfigurationHashFile());
      return storedHash.equals(GENERATED_BY_COMPILER_HASH_MARK) || storedHash.equals(getProjectConfigurationHash());
    }
    catch (IOException e) {
      return false;
    }
  }

  private String getProjectConfigurationHash() throws IOException {
    if (myProjectConfigurationHash == null) {
      myProjectConfigurationHash = computeProjectConfigurationHash();
    }
    return myProjectConfigurationHash;
  }

  @NotNull
  private String computeProjectConfigurationHash() throws IOException {
    long start = System.currentTimeMillis();
    File modulesXmlFile = new File(myIdeaProjectHome, ".idea/modules.xml");
    XMLInputFactory factory = XMLInputFactory.newFactory();
    BufferedInputStream input = new BufferedInputStream(new FileInputStream(modulesXmlFile));
    List<File> projectFiles = new ArrayList<File>();
    projectFiles.add(modulesXmlFile);
    try {
      XMLStreamReader reader = factory.createXMLStreamReader(input);
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("module")) {
          String attributeName = reader.getAttributeLocalName(0);
          if (attributeName.equals("filepath")) {
            String path = reader.getAttributeValue(0);
            projectFiles.add(new File(path.replace("$PROJECT_DIR$", myIdeaProjectHome.getAbsolutePath())));
          }
        }
      }

    }
    catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
    finally {
      input.close();
    }
    File[] libraryFiles = new File(myIdeaProjectHome, ".idea/libraries").listFiles(FileFilters.filesWithExtension("xml"));
    if (libraryFiles != null) {
      projectFiles.addAll(Arrays.asList(libraryFiles));
    }

    long hash = 0;
    for (File file : projectFiles) {
      hash = hash * 31 + file.getName().hashCode();
      hash = hash * 31 + file.lastModified();
    }
    System.out.println("Project configuration hash computed in " + (System.currentTimeMillis()-start) + "ms");
    return String.valueOf(hash);
  }

  private File getProjectConfigurationHashFile() {
    return new File(myOutputDir, MODULE_DESCRIPTORS_DIR_NAME + "/" + PROJECT_CONFIGURATION_HASH_FILE_NAME);
  }

  private void runGenerator() {
    try {
      String[] modules = {
        "jps-model-api", "jps-model-serialization", "platform-loader", "jps-model-impl", "devkit-jps-model",
        "platform-module-descriptors-generator", "jps-plugin-system"
      };
      List<URL> classpath = new ArrayList<URL>();
      for (String path : PathManager.getUtilClassPath()) {
        classpath.add(new File(path).toURI().toURL());
      }
      for (String module : modules) {
        File file = new File(myOutputDir, "production/" + module);
        if (!file.exists()) {
          throw new PlatformLoaderException("Cannot generate descriptors: " + file.getAbsolutePath() + " doesn't exist");
        }
        classpath.add(file.toURI().toURL());
      }
      UrlClassLoader classLoader = UrlClassLoader.build().urls(classpath).get();
      Class<?> aClass = Class.forName("org.jetbrains.jps.devkit.builder.RuntimeModuleDescriptorsGenerator", true, classLoader);
      aClass.getMethod("generate", String.class, String.class, File.class).invoke(null, myIdeaProjectHome.getAbsolutePath(), null, myIdeDist);
      FileUtil.writeToFile(getProjectConfigurationHashFile(), getProjectConfigurationHash());
    }
    catch (Exception e) {
      throw new PlatformLoaderException("Failed to generate module descriptors", e);
    }
  }
}
