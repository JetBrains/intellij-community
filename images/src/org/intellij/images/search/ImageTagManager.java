/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.images.search;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

@State(name = "ImageTags", storages = @Storage("imageTags.xml"))
public class ImageTagManager implements PersistentStateComponent<ImageTagManager.State> {

  private State myState = new State();

  public static ImageTagManager getInstance(Project project) {
    return ServiceManager.getService(project, ImageTagManager.class);
  }

  public boolean hasTag(String tag, VirtualFile file) {
    State.Files files = myState.myTags.get(tag);
    return files != null && files.contains(file.getPath());
  }

  public void addTag(String tag, VirtualFile file) {
    State.Files files = myState.myTags.get(tag);
    if (files == null) {
      files = new State.Files();
      myState.myTags.put(tag, files);
    }
    files.add(file.getPath());
  }

  public void removeTag(String tag, VirtualFile file) {
    State.Files files = myState.myTags.get(tag);
    if (files != null) {
      files.remove(file.getPath());
    }
  }

  public List<String> getTags(VirtualFile file) {
    return myState.myTags.keySet()
      .stream()
      .filter(tag -> hasTag(tag, file))
      .collect(Collectors.toList());
  }

  public List<String> getAllTags() {
    return new ArrayList<>(myState.myTags.keySet());
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public static class State {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundKeyWithTag = false, surroundWithTag = false,
      entryTagName = "tag", keyAttributeName = "name", valueAttributeName = "values")
    public final Map<String, Files> myTags = new LinkedHashMap<>();

    @Property(surroundWithTag = false)
    @Tag("files")
    public static class Files {
      @Property(surroundWithTag = false)
      @AbstractCollection(surroundWithTag = false, elementTag = "file", elementValueAttribute = "path")
      public final Set<String> myFiles = new LinkedHashSet<>();

      public void remove(String path) {
        myFiles.remove(path);
      }

      public void add(String path) {
        myFiles.add(path);
      }

      public boolean contains(String path) {
        return myFiles.contains(path);
      }
    }
  }
}
