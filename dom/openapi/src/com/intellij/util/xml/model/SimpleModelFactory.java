package com.intellij.util.xml.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomElement;
import com.intellij.psi.xml.XmlFile;

import java.util.Set;

/**
 * User: Sergey.Vasiliev
 */
public interface SimpleModelFactory<T extends DomElement, M extends DomModel<T>> {

  @Nullable
  M getModelByConfigFile(@Nullable XmlFile psiFile);

  @NotNull
  DomFileElement<T> createMergedModelRoot(Set<XmlFile> configFiles);
}
