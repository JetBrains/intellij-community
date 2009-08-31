package com.intellij.conversion;

import org.jdom.Element;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * @author nik
 */
public interface ModuleSettings extends ComponentManagerSettings {

  @NotNull
  String getModuleName();

  @Nullable
  String getModuleType();

  @NotNull
  File getModuleFile();

  @NotNull
  Collection<? extends Element> getFacetElements(@NotNull String facetTypeId);

  @NotNull
  Element getRootElement();

  @NotNull
  Document getDocument();

  void setModuleType(@NotNull String moduleType);
}
