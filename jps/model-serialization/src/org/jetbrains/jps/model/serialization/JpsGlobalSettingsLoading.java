// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

@ApiStatus.Internal
public final class JpsGlobalSettingsLoading {
  public static final String SDK_TABLE_COMPONENT_NAME = "ProjectJdkTable";
  private static final JpsGlobalExtensionSerializer[] SERIALIZERS = {
    new GlobalLibrariesSerializer(), new SdkTableSerializer(), JpsGlobalLoader.FILE_TYPES_SERIALIZER
  };

  private JpsGlobalSettingsLoading() {}

  public static void loadGlobalSettings(JpsGlobal global, String optionsPath) throws IOException {
    JpsGlobalLoader.configurePathMapper(global);
    Path optionsDir = Paths.get(FileUtil.toCanonicalPath(optionsPath));
    Map<String, String> pathVariables = loadPathVariables(global, optionsDir);
    createInstance(global, pathVariables).load(optionsDir);
  }

  private static @NotNull JpsGlobalLoader createInstance(JpsGlobal global, Map<String, String> pathVariables) {
    return new JpsGlobalLoader(new JpsMacroExpander(pathVariables), global, SERIALIZERS);
  }

  public static Map<String, String> loadPathVariables(JpsGlobal global, Path optionsDir) {
    createInstance(global, Collections.emptyMap()).loadGlobalComponents(optionsDir, optionsDir.resolve("other.xml"), new JpsGlobalLoader.PathVariablesSerializer());
    return JpsModelSerializationDataService.computeAllPathVariables(global);
  }

  public static Map<String, String> computeAllPathVariables(@NotNull String optionsPath) {
    JpsModel model = JpsElementFactory.getInstance().createModel();
    Path optionsDir = Paths.get(FileUtil.toCanonicalPath(optionsPath));
    return loadPathVariables(model.getGlobal(), optionsDir);
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
}
