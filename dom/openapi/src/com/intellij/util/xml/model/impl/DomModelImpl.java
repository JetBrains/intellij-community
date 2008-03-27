/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.xml.model.impl;

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.MergedObject;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class DomModelImpl<T extends DomElement> {

  protected final DomFileElement<T> myMergedModel;
  protected final Set<XmlFile> myConfigFiles;

  /**
   * Using this method may result in a large memory usage, since it will keep all the DOM and PSI for all the config files
   * @return
   */
  @Deprecated
  public DomModelImpl(@NotNull T mergedModel, @NotNull Set<XmlFile> configFiles) {
    myMergedModel = mergedModel.getRoot();
    myConfigFiles = configFiles;
  }

  public DomModelImpl(@NotNull DomFileElement<T> mergedModel, @NotNull Set<XmlFile> configFiles) {
    myMergedModel = mergedModel.getRoot();
    myConfigFiles = configFiles;
  }

  @NotNull
  public T getMergedModel() {
    return myMergedModel.getRootElement();
  }

  @NotNull
  public Set<XmlFile> getConfigFiles() {
    return myConfigFiles;
  }

  @NotNull
  public List<DomFileElement<T>> getRoots() {
    return myMergedModel instanceof MergedObject ? ((MergedObject) myMergedModel).getImplementations() : Collections.singletonList(myMergedModel);
  }

}
