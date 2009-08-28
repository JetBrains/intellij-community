package com.intellij.conversion;

import org.jdom.Element;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public interface WorkspaceSettings extends ComponentManagerSettings {

  @NotNull
  Collection<? extends Element> getRunConfigurations();

  @NotNull
  Element getRootElement();

  @NotNull
  Document getDocument();
}
