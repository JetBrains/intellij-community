package com.intellij.conversion;

import org.jetbrains.annotations.NotNull;
import org.jdom.Element;

import java.util.Collection;

/**
 * @author nik
 */
public interface RunManagerSettings {
  @NotNull
  Collection<? extends Element> getRunConfigurations();
}
