// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.serialization.impl.JpsPathVariablesConfigurationImpl;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public final class JpsGlobalLoader extends JpsLoaderBase {
  private static final Logger LOG = Logger.getInstance(JpsGlobalLoader.class);
  public static final String SDK_TABLE_COMPONENT_NAME = "ProjectJdkTable";
  public static final JpsElementChildRole<JpsPathVariablesConfiguration> PATH_VARIABLES_ROLE = JpsElementChildRoleBase.create("path variables");
  private static final JpsGlobalExtensionSerializer[] SERIALIZERS = {
    new GlobalLibrariesSerializer(), new SdkTableSerializer(), new FileTypesSerializer()
  };
  private final JpsGlobal myGlobal;

  private JpsGlobalLoader(JpsGlobal global, Map<String, String> pathVariables) {
    super(new JpsMacroExpander(pathVariables));
    myGlobal = global;
  }

  public static void loadGlobalSettings(JpsGlobal global, String optionsPath) throws IOException {
    if (System.getProperty("jps.in.wsl") != null) {
      global.setPathMapper(new JpsWslPathMapper());
    }
    Path optionsDir = Paths.get(FileUtil.toCanonicalPath(optionsPath));
    Map<String, String> pathVariables = loadPathVariables(global, optionsDir);
    new JpsGlobalLoader(global, pathVariables).load(optionsDir);
  }

  private static Map<String, String> loadPathVariables(JpsGlobal global, Path optionsDir) {
    new JpsGlobalLoader(global, Collections.emptyMap()).loadGlobalComponents(optionsDir, optionsDir.resolve("other.xml"), new PathVariablesSerializer());
    return JpsModelSerializationDataService.computeAllPathVariables(global);
  }

  public static Map<String, String> computeAllPathVariables(@NotNull String optionsPath) {
    JpsModel model = JpsElementFactory.getInstance().createModel();
    Path optionsDir = Paths.get(FileUtil.toCanonicalPath(optionsPath));
    return loadPathVariables(model.getGlobal(), optionsDir);
  }

  private void load(@NotNull Path optionsDir) {
    Path defaultConfigFile = optionsDir.resolve("other.xml");
    LOG.debug("Loading config from " + optionsDir.toAbsolutePath());
    for (JpsGlobalExtensionSerializer serializer : SERIALIZERS) {
      loadGlobalComponents(optionsDir, defaultConfigFile, serializer);
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsGlobalExtensionSerializer serializer : extension.getGlobalExtensionSerializers()) {
        loadGlobalComponents(optionsDir, defaultConfigFile, serializer);
      }
    }
  }

  private void loadGlobalComponents(@NotNull Path optionsDir, @NotNull Path defaultConfigFile, JpsGlobalExtensionSerializer serializer) {
    loadComponents(optionsDir, defaultConfigFile.getParent(), serializer, myGlobal);
  }

  public static final class PathVariablesSerializer extends JpsGlobalExtensionSerializer {
    public static final String MACRO_TAG = "macro";
    public static final String NAME_ATTRIBUTE = "name";
    public static final String VALUE_ATTRIBUTE = "value";
    public static final String STORAGE_FILE_NAME = "path.macros.xml";

    public PathVariablesSerializer() {
      super(STORAGE_FILE_NAME, "PathMacrosImpl");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      JpsPathVariablesConfiguration configuration = global.getContainer().setChild(PATH_VARIABLES_ROLE, new JpsPathVariablesConfigurationImpl());
      for (Element macroTag : JDOMUtil.getChildren(componentTag, MACRO_TAG)) {
        String name = macroTag.getAttributeValue(NAME_ATTRIBUTE);
        String value = macroTag.getAttributeValue(VALUE_ATTRIBUTE);
        if (name != null && value != null) {
          configuration.addPathVariable(name, StringUtil.trimEnd(FileUtil.toSystemIndependentName(value), "/"));
        }
      }
    }
  }

  public static final class GlobalLibrariesSerializer extends JpsGlobalExtensionSerializer {
    public GlobalLibrariesSerializer() {
      super("applicationLibraries.xml", "libraryTable");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      JpsLibraryTableSerializer.loadLibraries(componentTag, global.getPathMapper(), global.getLibraryCollection());
    }
  }

  public static final class SdkTableSerializer extends JpsGlobalExtensionSerializer {
    public SdkTableSerializer() {
      super("jdk.table.xml", SDK_TABLE_COMPONENT_NAME);
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      JpsSdkTableSerializer.loadSdks(componentTag, global.getLibraryCollection(), global.getPathMapper());
    }
  }

  private static final class FileTypesSerializer extends JpsGlobalExtensionSerializer {
    private FileTypesSerializer() {
      super("filetypes.xml", System.getProperty("jps.file.types.component.name", "FileTypeManager"));
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      Element ignoreFilesTag = componentTag.getChild("ignoreFiles");
      if (ignoreFilesTag != null) {
        global.getFileTypesConfiguration().setIgnoredPatternString(ignoreFilesTag.getAttributeValue("list"));
      }
    }
  }
}
