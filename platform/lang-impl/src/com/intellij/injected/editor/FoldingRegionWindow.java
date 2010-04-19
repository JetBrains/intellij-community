/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.impl.FoldRegionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
public class FoldingRegionWindow extends RangeMarkerWindow implements FoldRegion {
  private final EditorWindow myEditorWindow;

  private final FoldRegionImpl myHostRegion;

  public FoldingRegionWindow(@NotNull DocumentWindow documentWindow, @NotNull EditorWindow editorWindow, @NotNull FoldRegionImpl hostRegion) {
    super(documentWindow, hostRegion);
    myEditorWindow = editorWindow;
    myHostRegion = hostRegion;
  }

  public boolean isExpanded() {
    return myHostRegion.isExpanded();
  }

  public void setExpanded(boolean expanded) {
    myHostRegion.setExpanded(expanded);
  }

  @NotNull
  public String getPlaceholderText() {
    return myHostRegion.getPlaceholderText();
  }

  public Editor getEditor() {
    return myEditorWindow;
  }

  public FoldingGroup getGroup() {
    return myHostRegion.getGroup();
  }

  public FoldRegionImpl getDelegate() {
    return myHostRegion;
  }
}
