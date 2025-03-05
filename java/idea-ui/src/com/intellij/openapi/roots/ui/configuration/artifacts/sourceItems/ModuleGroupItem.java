// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ModuleGroupItem extends PackagingSourceItem {
  private final @NlsSafe String myGroupName;
  private final List<String> myPath;

  public ModuleGroupItem(@NotNull List<@NlsSafe String> path) {
    super(false);
    myGroupName = path.get(path.size() - 1);
    myPath = path;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ModuleGroupItem && myPath.equals(((ModuleGroupItem)obj).myPath);
  }

  @Override
  public int hashCode() {
    return myPath.hashCode();
  }

  @Override
  public @NotNull SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new ModuleGroupSourceItemPresentation(myGroupName);
  }

  @Override
  public @NotNull List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    return Collections.emptyList();
  }

  public List<String> getPath() {
    return myPath;
  }

  private static class ModuleGroupSourceItemPresentation extends SourceItemPresentation {
    private final @NlsContexts.Label String myGroupName;

    ModuleGroupSourceItemPresentation(@NlsContexts.Label String groupName) {
      myGroupName = groupName;
    }

    @Override
    public String getPresentableName() {
      return myGroupName;
    }

    @Override
    public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                       SimpleTextAttributes commentAttributes) {
      presentationData.setIcon(PlatformIcons.CLOSED_MODULE_GROUP_ICON);
      presentationData.addText(myGroupName, mainAttributes);
    }

    @Override
    public int getWeight() {
      return SourceItemWeights.MODULE_GROUP_WEIGHT;
    }
  }
}
