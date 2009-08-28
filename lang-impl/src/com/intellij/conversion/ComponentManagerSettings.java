package com.intellij.conversion;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface ComponentManagerSettings {

  @Nullable
  Element getComponentElement(@NotNull @NonNls String componentName);

}
