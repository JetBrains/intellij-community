// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.intellilang.model.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.intellilang.instrumentation.InstrumentationType;
import org.jetbrains.jps.intellilang.model.JpsIntelliLangExtensionService;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;

/**
 * @author Eugene Zhuravlev
 */
public final class JpsIntelliLangConfigurationSerializer extends JpsGlobalExtensionSerializer {
  private static final Logger LOG = Logger.getInstance(JpsIntelliLangConfigurationSerializer.class);
  private static final String INSTRUMENTATION_TYPE_NAME = "INSTRUMENTATION";
  private static final String PATTERN_ANNOTATION_NAME = "PATTERN_ANNOTATION";

  JpsIntelliLangConfigurationSerializer() {
    super("IntelliLang.xml", "LanguageInjectionConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
    JpsIntelliLangConfigurationImpl configuration = new JpsIntelliLangConfigurationImpl();

    String annotationName = JDOMExternalizerUtil.readField(componentTag, PATTERN_ANNOTATION_NAME);
    if (annotationName != null) {
      configuration.setPatternAnnotationClassName(annotationName);
    }

    String instrumentationType = JDOMExternalizerUtil.readField(componentTag, INSTRUMENTATION_TYPE_NAME);
    if (instrumentationType != null) {
      try {
        configuration.setInstrumentationType(InstrumentationType.valueOf(instrumentationType));
      }
      catch (IllegalArgumentException e) {
        LOG.info(e);
      }
    }

    JpsIntelliLangExtensionService.getInstance().setConfiguration(global, configuration);
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsGlobal global) {
    JpsIntelliLangExtensionService.getInstance().setConfiguration(global, new JpsIntelliLangConfigurationImpl());
  }
}