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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author nik
 */
public abstract class PutIntoDefaultLocationActionBase extends AnAction {
  protected final SourceItemsTree mySourceItemsTree;
  protected final ArtifactEditorEx myArtifactEditor;

  public PutIntoDefaultLocationActionBase(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    mySourceItemsTree = sourceItemsTree;
    myArtifactEditor = artifactEditor;
  }

  @Nullable
  protected String getDefaultPath(PackagingSourceItem item) {
    return myArtifactEditor.getArtifact().getArtifactType().getDefaultPathFor(item);
  }

  protected static String getTargetLocationText(Set<String> paths) {
    String target;
    if (paths.size() == 1) {
      final String path = StringUtil.trimStart(StringUtil.trimEnd(paths.iterator().next(), "/"), "/");
      if (path.length() > 0) {
        target = "/" + path;
      }
      else {
        target = "Output Root";
      }
    }
    else {
      target = "Default Locations";
    }
    return target;
  }
}
