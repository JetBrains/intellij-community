package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;

public interface ExternalizableScheme extends Scheme{
  @NotNull
  ExternalInfo getExternalInfo();

  void setName(String newName);
}
