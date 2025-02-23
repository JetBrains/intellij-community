// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.serialization.impl.JpsPathVariablesConfigurationImpl;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApiStatus.Internal
public class JpsGlobalLoader {
  public static final JpsElementChildRole<JpsPathVariablesConfiguration> PATH_VARIABLES_ROLE = JpsElementChildRoleBase.create("path variables");
  public static final JpsGlobalExtensionSerializer FILE_TYPES_SERIALIZER = new FileTypesSerializer();
  private static final Logger LOG = Logger.getInstance(JpsGlobalLoader.class);
  private final JpsGlobal myGlobal;
  private final JpsGlobalExtensionSerializer @NotNull [] myBundledSerializers;
  private final JpsComponentLoader myComponentLoader;

  public JpsGlobalLoader(JpsMacroExpander macroExpander, JpsGlobal global, JpsGlobalExtensionSerializer @NotNull [] bundledSerializers) {
    myComponentLoader = new JpsComponentLoader(macroExpander, null);
    myGlobal = global;
    myBundledSerializers = bundledSerializers;
  }

  public void load(@NotNull Path optionsDir) {
    Path defaultConfigFile = optionsDir.resolve("other.xml");
    LOG.debug("Loading config from " + optionsDir.toAbsolutePath());
    for (JpsGlobalExtensionSerializer serializer : myBundledSerializers) {
      loadGlobalComponents(optionsDir, defaultConfigFile, serializer);
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsGlobalExtensionSerializer serializer : extension.getGlobalExtensionSerializers()) {
        loadGlobalComponents(optionsDir, defaultConfigFile, serializer);
      }
    }
  }

  public static void configurePathMapper(JpsGlobal global) {
    if (System.getProperty("jps.in.wsl") != null) {
      global.setPathMapper(new JpsWslPathMapper());
      return;
    }
    Set<String> pathPrefixes = Optional.ofNullable(System.getProperty("ide.jps.remote.path.prefixes"))
      .map(s -> s.split(";"))
      .stream()
      .flatMap(Arrays::stream)
      .collect(Collectors.toSet());
    if (!pathPrefixes.isEmpty()) {
      global.setPathMapper(new JpsPrefixesCuttingPathMapper(pathPrefixes));
    }
  }

  protected void loadGlobalComponents(@NotNull Path optionsDir, @NotNull Path defaultConfigFile, JpsGlobalExtensionSerializer serializer) {
    myComponentLoader.loadComponents(optionsDir, defaultConfigFile.getParent(), serializer, myGlobal);
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
          configuration.addPathVariable(name, StringUtil.trimEnd(FileUtilRt.toSystemIndependentName(value), "/"));
        }
      }
    }
  }

  private static final class FileTypesSerializer extends JpsGlobalExtensionSerializer {
    FileTypesSerializer() {
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
