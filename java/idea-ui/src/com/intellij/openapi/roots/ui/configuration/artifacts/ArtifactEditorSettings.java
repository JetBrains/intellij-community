/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactEditorSettings implements PersistentStateComponent<ArtifactEditorSettings.ArtifactEditorSettingsState> {
  private boolean mySortElements = true;
  private final List<ComplexPackagingElementType<?>> myTypesToShowContent = new ArrayList<>();

  public ArtifactEditorSettings() {
  }

  public ArtifactEditorSettings(boolean sortElements, Collection<ComplexPackagingElementType<?>> typesToShowContent) {
    mySortElements = sortElements;
    myTypesToShowContent.addAll(typesToShowContent);
  }

  @Override
  public ArtifactEditorSettingsState getState() {
    final ArtifactEditorSettingsState state = new ArtifactEditorSettingsState();
    state.mySortElements = mySortElements;
    for (ComplexPackagingElementType<?> type : myTypesToShowContent) {
      state.myTypesToShowContentIds.add(type.getId());
    }
    return state;
  }

  @Override
  public void loadState(ArtifactEditorSettingsState state) {
    mySortElements = state.mySortElements;
    myTypesToShowContent.clear();
    for (String id : state.myTypesToShowContentIds) {
      final PackagingElementType<?> type = PackagingElementFactory.getInstance().findElementType(id);
      if (type instanceof ComplexPackagingElementType<?>) {
        myTypesToShowContent.add((ComplexPackagingElementType<?>)type);
      }
    }
  }

  public boolean isSortElements() {
    return mySortElements;
  }

  public List<ComplexPackagingElementType<?>> getTypesToShowContent() {
    return myTypesToShowContent;
  }

  public void setSortElements(boolean sortElements) {
    mySortElements = sortElements;
  }

  public void setTypesToShowContent(Collection<ComplexPackagingElementType<?>> typesToShowContent) {
    myTypesToShowContent.clear();
    myTypesToShowContent.addAll(typesToShowContent);
  }

  @Tag("artifact-editor")
  public static class ArtifactEditorSettingsState {
    @Tag("show-sorted")
    public boolean mySortElements = true;
    @Tag("show-content")
    @AbstractCollection(surroundWithTag = false, elementTag = "type", elementValueAttribute = "id")
    public List<String> myTypesToShowContentIds = new ArrayList<>();
  }
}
