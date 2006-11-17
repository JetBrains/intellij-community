/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.model;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ModelMerger;
import com.intellij.util.xml.DomElement;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class SimpleDomModelFactory<T extends DomElement> {
  protected final Class<T> myClass;
  protected final ModelMerger myModelMerger;

  public SimpleDomModelFactory(@NotNull Class<T> aClass, @NotNull ModelMerger modelMerger) {
    myClass = aClass;
    myModelMerger = modelMerger;
  }

  @Nullable
  public T getDom(@NotNull XmlFile configFile) {
    final DomFileElement<T> element = DomManager.getDomManager(configFile.getProject()).getFileElement(configFile, myClass);
    return element == null ? null : element.getRootElement();
  }

  @NotNull
  public T createMergedModel(Set<XmlFile> configFiles) {
    List<T> configs = new ArrayList<T>(configFiles.size());
    for (XmlFile configFile : configFiles) {
      final T dom = getDom(configFile);
      if (dom != null) {
        configs.add(dom);
      }
    }

    return myModelMerger.mergeModels(myClass, configs);
  }
}
