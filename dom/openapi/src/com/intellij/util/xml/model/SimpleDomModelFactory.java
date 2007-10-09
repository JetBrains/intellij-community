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
