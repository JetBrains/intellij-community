/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

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
    @XCollection(elementName = "type", valueAttributeName = "id", propertyElementName = "show-content")
    public List<String> myTypesToShowContentIds = new ArrayList<>();
  }
}
