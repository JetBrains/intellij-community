// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactSerializer;
import org.jetbrains.jps.model.serialization.impl.JpsProjectSerializationDataExtensionImpl;
import org.jetbrains.jps.model.serialization.impl.TimingLog;
import org.jetbrains.jps.model.serialization.runConfigurations.JpsRunConfigurationSerializer;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Contains helper methods which are used to load parts of the project configuration which aren't stored in the workspace model.
 */
@ApiStatus.Internal
public final class JpsProjectConfigurationLoading {
  private JpsProjectConfigurationLoading() {
  }

  public static void setupSerializationExtension(@NotNull JpsProject project, @NotNull Path baseDir) {
    project.getContainer().setChild(JpsProjectSerializationDataExtensionImpl.ROLE, new JpsProjectSerializationDataExtensionImpl(baseDir));
  }

  public static JpsMacroExpander createProjectMacroExpander(Map<String, String> pathVariables, @NotNull Path baseDir) {
    JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    expander.addFileHierarchyReplacements(PathMacroUtil.PROJECT_DIR_MACRO_NAME, baseDir.toFile());
    return expander;
  }

  public static @Nullable Path getExternalConfigurationDirectoryFromSystemProperty() {
    String externalProjectConfigDir = System.getProperty("external.project.config");
    return externalProjectConfigDir != null && !externalProjectConfigDir.isBlank() 
                                          ? Path.of(externalProjectConfigDir) : null;
  }

  public static @NotNull String getDirectoryBaseProjectName(@NotNull Path basePath, @NotNull Path storeDir) {
    String name = JpsPathUtil.readProjectName(storeDir);
    return name != null ? name : NioFiles.getFileName(basePath);
  }

  public static void loadRunConfigurationsFromDirectory(@NotNull JpsProject project,
                                                        @NotNull JpsComponentLoader componentLoader,
                                                        @NotNull Path dir,
                                                        @NotNull Path workspaceFile) {
    if (hasRunConfigurationSerializers()) {
      Runnable runConfTimingLog = TimingLog.startActivity("loading run configurations");
      for (Path configurationFile : listXmlFiles(dir.resolve("runConfigurations"))) {
        JpsRunConfigurationSerializer.loadRunConfigurations(project, componentLoader.loadRootElement(configurationFile));
      }
      JpsRunConfigurationSerializer.loadRunConfigurations(project, componentLoader.loadComponent(workspaceFile, "RunManager"));
      runConfTimingLog.run();
    }
  }

  public static void loadArtifactsFromDirectory(@NotNull JpsProject project,
                                                @NotNull JpsComponentLoader componentLoader,
                                                @NotNull Path dir,
                                                @Nullable Path externalConfigDir) {
    Runnable artifactsTimingLog = TimingLog.startActivity("loading artifacts");
    for (Path artifactFile : listXmlFiles(dir.resolve("artifacts"))) {
      @Nullable Element artifactManagerComponent = componentLoader.loadRootElement(artifactFile);
      JpsArtifactSerializer.loadArtifacts(project, artifactManagerComponent);
    }
    if (externalConfigDir != null) {
      @Nullable Element artifactManagerComponent = componentLoader.loadRootElement(externalConfigDir.resolve("artifacts.xml"));
      JpsArtifactSerializer.loadArtifacts(project, artifactManagerComponent);
    }
    artifactsTimingLog.run();
  }

  private static boolean hasRunConfigurationSerializers() {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      if (!extension.getRunConfigurationPropertiesSerializers().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  static @Unmodifiable @NotNull List<Path> listXmlFiles(@NotNull Path dir) {
    try {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, it -> it.getFileName().toString().endsWith(".xml") && Files.isRegularFile(it))) {
        return ContainerUtil.collect(stream.iterator());
      }
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
  }

  public static void loadRunConfigurationsFromIpr(@NotNull JpsProject project, @Nullable Element iprRoot, @Nullable Element iwsRoot) {
    if (hasRunConfigurationSerializers()) {
      JpsRunConfigurationSerializer.loadRunConfigurations(project, JDomSerializationUtil.findComponent(iprRoot, "ProjectRunConfigurationManager"));
      JpsRunConfigurationSerializer.loadRunConfigurations(project, JDomSerializationUtil.findComponent(iwsRoot, "RunManager"));
    }
  }

  public static void loadArtifactsFromIpr(@NotNull JpsProject project, @Nullable Element iprRoot) {
    @Nullable Element artifactManagerComponent = JDomSerializationUtil.findComponent(iprRoot, "ArtifactManager");
    JpsArtifactSerializer.loadArtifacts(project, artifactManagerComponent);
  }

  public static void loadProjectExtensionsFromIpr(@NotNull JpsProject project, @Nullable Element iprRoot, @Nullable Element iwsRoot) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsProjectExtensionSerializer serializer : extension.getProjectExtensionSerializers()) {
        Element rootTag = JpsProjectExtensionSerializer.WORKSPACE_FILE.equals(serializer.getConfigFileName()) ? iwsRoot : iprRoot;
        Element component = JDomSerializationUtil.findComponent(rootTag, serializer.getComponentName());
        if (component != null) {
          serializer.loadExtension(project, component);
        }
        else {
          serializer.loadExtensionWithDefaultSettings(project);
        }
      }
    }
  }

  public static @Nullable Pair<String, String> readProjectSdkTypeAndName(@Nullable Element rootElement) {
    Pair<String, String> sdkTypeIdAndName = null;
    Element rootManagerElement = JDomSerializationUtil.findComponent(rootElement, "ProjectRootManager");
    if (rootManagerElement != null) {
      String sdkName = rootManagerElement.getAttributeValue("project-jdk-name");
      String sdkTypeId = rootManagerElement.getAttributeValue("project-jdk-type");
      if (sdkName != null) {
        sdkTypeIdAndName = new Pair<>(Objects.requireNonNullElse(sdkTypeId, "JavaSDK"), sdkName);
      }
    }
    return sdkTypeIdAndName;
  }

  public static JpsMacroExpander createModuleMacroExpander(final Map<String, String> pathVariables, @NotNull Path moduleFile) {
    JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    String moduleDirPath = PathMacroUtil.getModuleDir(moduleFile.toAbsolutePath().toString());
    if (moduleDirPath != null) {
      expander.addFileHierarchyReplacements(PathMacroUtil.MODULE_DIR_MACRO_NAME, new File(FileUtilRt.toSystemDependentName(moduleDirPath)));
    }
    return expander;
  }

  public static @NotNull Set<String> readNamesOfUnloadedModules(@NotNull Path workspaceFile, @NotNull JpsComponentLoader componentLoader) {
    Set<String> unloadedModules = new HashSet<>();
    if (workspaceFile.toFile().exists()) {
      Element unloadedModulesList = componentLoader.loadComponent(workspaceFile, "UnloadedModulesList");
      for (Element element : JDOMUtil.getChildren(unloadedModulesList, "module")) {
        unloadedModules.add(element.getAttributeValue("name"));
      }
    }
    return unloadedModules;
  }
}
