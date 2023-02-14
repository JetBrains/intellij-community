/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.intellij.images.editor.impl;

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.TransferableFileEditorState;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class ImageFileEditorState implements TransferableFileEditorState, Serializable {
  private static final long serialVersionUID = -4470317464706072486L;
  public static final String IMAGE_EDITOR_ID = "ImageEditor";
  public static final String BACKGROUND_VISIBLE_OPTION = "backgroundVisible";
  public static final String GRID_VISIBLE_OPTION = "gridVisible";
  public static final String ZOOM_FACTOR_OPTION = "zoomFactor";
  public static final String ZOOM_FACTOR_CHANGED_OPTION = "zoomFactorChanged";

  private boolean backgroundVisible;
  private boolean gridVisible;
  private double zoomFactor;
  private boolean zoomFactorChanged;

  public ImageFileEditorState(boolean backgroundVisible, boolean gridVisible, double zoomFactor, boolean zoomFactorChanged) {
    this.backgroundVisible = backgroundVisible;
    this.gridVisible = gridVisible;
    this.zoomFactor = zoomFactor;
    this.zoomFactorChanged = zoomFactorChanged;
  }

  @Override
  public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
    return otherState instanceof ImageFileEditorState;
  }

  public boolean isBackgroundVisible() {
    return backgroundVisible;
  }

  public boolean isGridVisible() {
    return gridVisible;
  }

  public double getZoomFactor() {
    return zoomFactor;
  }

  public boolean isZoomFactorChanged() {
    return zoomFactorChanged;
  }

  @Override
  public void setCopiedFromMasterEditor() {
    zoomFactorChanged = true;
  }

  @Override
  public String getEditorId() {
    return IMAGE_EDITOR_ID;
  }

  @Override
  public Map<String, String> getTransferableOptions() {
    final HashMap<String, String> map = new HashMap<>();
    map.put(BACKGROUND_VISIBLE_OPTION, String.valueOf(backgroundVisible));
    map.put(GRID_VISIBLE_OPTION, String.valueOf(gridVisible));
    map.put(ZOOM_FACTOR_OPTION, String.valueOf(zoomFactor));
    map.put(ZOOM_FACTOR_CHANGED_OPTION, String.valueOf(zoomFactorChanged));
    return map;
  }

  @Override
  public void setTransferableOptions(Map<String, String> options) {
    String o = options.get(BACKGROUND_VISIBLE_OPTION);
    if (o != null) {
      backgroundVisible = Boolean.parseBoolean(o);
    }

    o = options.get(GRID_VISIBLE_OPTION);
    if (o != null) {
      gridVisible = Boolean.parseBoolean(o);
    }

    o = options.get(ZOOM_FACTOR_OPTION);
    if (o != null) {
      zoomFactor = Double.parseDouble(o);
    }

    o = options.get(ZOOM_FACTOR_CHANGED_OPTION);
    if (o != null) {
      zoomFactorChanged = Boolean.parseBoolean(o);
    }
  }
}
