// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.search;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "ImageTags", storages = @Storage("imageTags.xml"))
public class ImageTagManager implements PersistentStateComponent<ImageTagManager.State> {

  private State myState = new State();

  public static ImageTagManager getInstance(Project project) {
    return project.getService(ImageTagManager.class);
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
    return ContainerUtil.filter(myState.myTags.keySet(), tag -> hasTag(tag, file));
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
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static class State {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundKeyWithTag = false, surroundWithTag = false,
      entryTagName = "tag", keyAttributeName = "name", valueAttributeName = "values")
    public final Map<@NlsSafe String, Files> myTags = new LinkedHashMap<>();

    @Property(surroundWithTag = false)
    @Tag("files")
    public static class Files {
      @Property(surroundWithTag = false)
      @XCollection(elementName = "file", valueAttributeName = "path")
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
