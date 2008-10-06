package com.intellij.openapi.components;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;


public interface XmlConfigurationMerger {
  @NotNull
  Element merge(Element original, Element mergeWith);

  String getComponentName();
}
