// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.jps.model.serialization.resolver;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import com.intellij.platform.jps.model.resolver.JpsDependencyResolverConfigurationService;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.EnumMap;

public class JpsDependencyResolverConfigurationSerializer extends JpsProjectExtensionSerializer {
  private static final String CONFIG_FILE_NAME = "dependencyResolver.xml";
  private static final String COMPONENT_NAME = "MavenDependencyResolverConfiguration";

  private static final String OPTION_TAG = "option";
  private static final String NAME_ATTRIBUTE = "name";
  private static final String VALUE_ATTRIBUTE = "value";

  private enum Options {
    VERIFY_SHA256_CHECKSUMS, USE_BIND_REPOSITORY
  }

  public JpsDependencyResolverConfigurationSerializer() {
    super(CONFIG_FILE_NAME, COMPONENT_NAME);
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    var optionsValues = new EnumMap<Options, Boolean>(Options.class);
    var optionElements = componentTag.getChildren(OPTION_TAG);
    for (var element : optionElements) {
      try {
        var name = Options.valueOf(element.getAttributeValue(NAME_ATTRIBUTE));
        var value = Boolean.parseBoolean(element.getAttributeValue(VALUE_ATTRIBUTE));
        optionsValues.put(name, value);
      }
      catch (Exception ignored) {
      }
    }

    var resolverConfigurationService = JpsDependencyResolverConfigurationService.getInstance();
    var config = resolverConfigurationService.getOrCreateDependencyResolverConfiguration(project);

    if (optionsValues.containsKey(Options.VERIFY_SHA256_CHECKSUMS)) {
      config.setSha256ChecksumVerificationEnabled(optionsValues.get(Options.VERIFY_SHA256_CHECKSUMS));
    }
    if (optionsValues.containsKey(Options.USE_BIND_REPOSITORY)) {
      config.setBindRepositoryEnabled(optionsValues.get(Options.USE_BIND_REPOSITORY));
    }
  }
}
