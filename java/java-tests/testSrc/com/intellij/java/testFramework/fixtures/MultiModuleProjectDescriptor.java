// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework.fixtures;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.conversion.ModuleSettings.MODULE_ROOT_MANAGER_COMPONENT;
import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static org.jetbrains.jps.model.serialization.JpsProjectLoader.FILE_PATH_ATTRIBUTE;
import static org.jetbrains.jps.model.serialization.JpsProjectLoader.MODULES_TAG;
import static org.jetbrains.jps.model.serialization.JpsProjectLoader.MODULE_MANAGER_COMPONENT;
import static org.jetbrains.jps.model.serialization.JpsProjectLoader.MODULE_TAG;
import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.IS_GENERATED_ATTRIBUTE;
import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID;
import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID;
import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.LANGUAGE_LEVEL_ATTRIBUTE;
import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.MODULE_LANGUAGE_LEVEL_ATTRIBUTE;
import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.OUTPUT_TAG;
import static org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.CONTENT_TAG;
import static org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.IS_TEST_SOURCE_ATTRIBUTE;
import static org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.SOURCE_FOLDER_TAG;
import static org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.TYPE_ATTRIBUTE;

public class MultiModuleProjectDescriptor extends DefaultLightProjectDescriptor {
  private final Path myPath;
  @Nullable private final String myMainModuleName;
  @Nullable private final Consumer<ProjectModel> myProcess;
  private final Path myProjectPath;
  @Nullable private LanguageLevel myLanguageLevel;

  public MultiModuleProjectDescriptor(@NotNull Path path, @Nullable String mainModuleName, @Nullable Consumer<ProjectModel> process) {
    myPath = path;
    myMainModuleName = mainModuleName;
    myProcess = process;
    myProjectPath = TemporaryDirectory.generateTemporaryPath("project/before/" + ProjectImpl.LIGHT_PROJECT_NAME);
  }

  public Path getBeforePath() {
    return myPath.resolve("before");
  }

  public Path getAfterPath() {
    return myPath.resolve("after");
  }

  @Override
  public @NotNull Path generateProjectPath() {
    return myProjectPath;
  }

  public @NotNull Path getProjectPath() {
    return myProjectPath;
  }

  @Override
  public Sdk getSdk() {
    return myLanguageLevel != null
    ? IdeaTestUtil.getMockJdk(myLanguageLevel.toJavaVersion())
    : IdeaTestUtil.getMockJdk11();
  }

  @Override
  public void setUpProject(@NotNull Project project, @NotNull SetupHandler handler) throws Exception {
    WriteAction.run(() -> {
      FileUtilRt.deleteRecursively(myProjectPath);
      FileUtil.copyDir(getBeforePath().toFile(), myProjectPath.toFile());
      VfsUtil.markDirtyAndRefresh(false, true, true, myProjectPath.toFile());
      ProjectModel projectModel = new ProjectModel(project, myProjectPath);
      if (myProcess != null) myProcess.accept(projectModel);
      myLanguageLevel = projectModel.getLanguageLevel();
      AcceptedLanguageLevelsSettings.allowLevel(project, myLanguageLevel);

      for (ModuleDescriptor descriptor : projectModel.getModules()) {
        Path iml = descriptor.basePath().resolve(descriptor.name() + ModuleFileType.DOT_DEFAULT_EXTENSION);
        final Module module = Files.exists(iml)
                              ? ModuleManager.getInstance(project).loadModule(iml)
                              : createModule(project, iml);
        if (myMainModuleName == null || module.getName().equals(myMainModuleName)) {
          handler.moduleCreated(module);
        }

        ModuleRootModificationUtil.updateModel(module, model -> {
          model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(descriptor.languageLevel());
          model.setSdk(IdeaTestUtil.getMockJdk(descriptor.languageLevel().toJavaVersion()));
          for (SourceDirectory source : descriptor.sources()) {
            final ContentEntry entry = model.addContentEntry(source.dir());
            switch (source.type()) {
              case RESOURCE:
                entry.addSourceFolder(source.dir(), JavaResourceRootType.RESOURCE);
                break;
              case TEST_RESOURCE:
                entry.addSourceFolder(source.dir(), JavaResourceRootType.TEST_RESOURCE);
                break;
              case TEST_SOURCE:
                entry.addSourceFolder(source.dir(), JavaSourceRootType.TEST_SOURCE);
                break;
              case SOURCE:
                entry.addSourceFolder(source.dir(), JavaSourceRootType.SOURCE);
                break;
              case GENERATED:
                final JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("");
                properties.setForGeneratedSources(true);
                entry.addSourceFolder(source.dir(), JavaSourceRootType.SOURCE, properties);
                break;
            }
            if (myMainModuleName == null || module.getName().equals(myMainModuleName)) {
              handler.sourceRootCreated(ProjectModel.urlToVirtualFile(source.dir()));
            }
          }

          // maven
          final Path mavenOutputPath = descriptor.basePath().resolve("target").resolve("classes");
          if (Files.exists(mavenOutputPath)) {
            final CompilerModuleExtension compiler = model.getModuleExtension(CompilerModuleExtension.class);
            compiler.setCompilerOutputPath(mavenOutputPath.toString());
            compiler.inheritCompilerOutputPath(false);
          }
        });
      }
    });
  }

  public static class ProjectModel {
    private final Path myProjectPath;
    private final Project myProject;
    private String myOutputUrl;
    private LanguageLevel myLanguageLevel;
    private final List<ModuleDescriptor> myModules = new ArrayList<>();

    private ProjectModel(Project project, Path path) {
      try {
        myProject = project;
        myProjectPath = path;
        load();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public String getOutputUrl() {
      return myOutputUrl;
    }

    public LanguageLevel getLanguageLevel() {
      return myLanguageLevel == null ? LanguageLevel.JDK_11 : myLanguageLevel;
    }

    public List<ModuleDescriptor> getModules() {
      return myModules;
    }

    public Project getProject() {
      return myProject;
    }

    private void load() throws IOException, JDOMException {
      final Path projectConfigurationPath = myProjectPath.resolve(DIRECTORY_STORE_FOLDER);
      final Element miscXml = JDomSerializationUtil.findComponent(JDOMUtil.load(projectConfigurationPath.resolve("misc.xml")),
                                                                  "ProjectRootManager");

      myLanguageLevel = parseLanguageLevel(miscXml.getAttributeValue(LANGUAGE_LEVEL_ATTRIBUTE), LanguageLevel.JDK_11);

      myOutputUrl = prepare(miscXml.getChild(OUTPUT_TAG).getAttributeValue(JpsJavaModelSerializerExtension.URL_ATTRIBUTE));

      final Element modulesXml = JDomSerializationUtil.findComponent(JDOMUtil.load(projectConfigurationPath.resolve("modules.xml")),
                                                                     MODULE_MANAGER_COMPONENT);
      final Element modulesElement = modulesXml.getChild(MODULES_TAG);
      final List<Element> moduleElements = modulesElement.getChildren(MODULE_TAG);
      for (Element moduleAttr : moduleElements) {
        final Path iml = Paths.get(prepare(moduleAttr.getAttributeValue(FILE_PATH_ATTRIBUTE))); // .iml
        final String moduleName = FileUtil.getNameWithoutExtension(iml.toFile()); // module name

        if (Files.exists(iml)) {
          final Element component = JDomSerializationUtil.findComponent(JDOMUtil.load(iml), MODULE_ROOT_MANAGER_COMPONENT);
          final LanguageLevel moduleLanguageLevel =
            parseLanguageLevel(component.getAttributeValue(MODULE_LANGUAGE_LEVEL_ATTRIBUTE), getLanguageLevel());
          final Element content = component.getChild(CONTENT_TAG);
          List<SourceDirectory> sources = new ArrayList<>();
          if (content != null) {
            for (Element element : content.getChildren(SOURCE_FOLDER_TAG)) {
              final String url = prepare(element.getAttributeValue(JpsModuleRootModelSerializer.URL_ATTRIBUTE),
                                         iml.getParent().toString());
              sources.add(new SourceDirectory(url, SourceType.of(element)));
            }
          }
          myModules.add(new ModuleDescriptor(moduleName, iml.getParent(), sources, moduleLanguageLevel));
        }
        else {
          myModules.add(new ModuleDescriptor(moduleName, iml.getParent(), List.of(), getLanguageLevel()));
        }
      }
    }

    @Nullable
    private static LanguageLevel parseLanguageLevel(@Nullable String level, LanguageLevel... levels) {
      LanguageLevel result = null;
      if (level != null) result = LanguageLevel.valueOf(level);
      if (result != null) return result;
      for (LanguageLevel languageLevel : levels) {
        if (languageLevel != null) return languageLevel;
      }
      return null;
    }

    @Contract("null->null")
    @Nullable
    private static VirtualFile urlToVirtualFile(@Nullable String url) {
      if (url == null) {
        return null;
      }
      else {
        return VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
      }
    }

    @NotNull
    private String prepare(@NotNull String path) {
      return path.replace("$PROJECT_DIR$", myProjectPath.toString());
    }

    @NotNull
    private String prepare(@NotNull String path, @NotNull String moduleDir) {
      return prepare(path).replace("$MODULE_DIR$", moduleDir);
    }
  }

  public record ModuleDescriptor(@NotNull String name, @NotNull Path basePath, @NotNull List<SourceDirectory> sources,
                                  @NotNull LanguageLevel languageLevel) {
  }

  private record SourceDirectory(@NotNull String dir, @NotNull SourceType type) {
  }

  private enum SourceType {
    GENERATED,
    RESOURCE,
    SOURCE,
    TEST_RESOURCE,
    TEST_SOURCE;

    @NotNull
    static SourceType of(@NotNull Element element) {
      if (Boolean.parseBoolean(element.getAttributeValue(IS_GENERATED_ATTRIBUTE))) return GENERATED;
      final String type = element.getAttributeValue(TYPE_ATTRIBUTE);
      if (JAVA_RESOURCE_ROOT_ID.equals(type)) return RESOURCE;
      if (JAVA_TEST_RESOURCE_ROOT_ID.equals(type)) return TEST_RESOURCE;
      return Boolean.parseBoolean(element.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE))
             ? TEST_SOURCE
             : SOURCE;
    }
  }
}
