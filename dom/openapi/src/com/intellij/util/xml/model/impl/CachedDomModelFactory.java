package com.intellij.util.xml.model.impl;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.model.DomModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Sergey.Vasiliev
 */
public interface CachedDomModelFactory <T extends DomElement, M extends DomModel<T>, Scope extends UserDataHolder> {
  @NotNull
  Object[] computeDependencies(@Nullable M model, @Nullable Scope scope);

  Scope getModelScope(@NotNull XmlFile xmlFile);
}
