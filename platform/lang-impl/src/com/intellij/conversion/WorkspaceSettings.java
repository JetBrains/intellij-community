package com.intellij.conversion;

import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface WorkspaceSettings extends ComponentManagerSettings {

  @NotNull
  Element getRootElement();

  @NotNull
  Document getDocument();
}
