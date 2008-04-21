package com.intellij.util.xml.model;

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * User: Sergey.Vasiliev
 */
public interface SimpleModelFactory<T extends DomElement, M extends DomModel<T>> {

  @Nullable
  M getModelByConfigFile(@Nullable XmlFile psiFile);

  @Nullable
  DomFileElement<T> createMergedModelRoot(Set<XmlFile> configFiles);
}
