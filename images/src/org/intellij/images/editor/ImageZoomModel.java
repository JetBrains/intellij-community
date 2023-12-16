/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.editor;

import org.intellij.images.options.ZoomOptions;
import org.jetbrains.annotations.Nullable;

/**
 * Location model presents bounds of image.
 * The zoom it calculated as y = exp(x/2).
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ImageZoomModel {
  int MACRO_ZOOM_POWER_LIMIT = 5;
  int MICRO_ZOOM_POWER_LIMIT = 8;
  double MACRO_ZOOM_RATIO = 2.0d;
  double MICRO_ZOOM_RATIO = 1.5d;
  double MACRO_ZOOM_LIMIT = Math.pow(MACRO_ZOOM_RATIO, MACRO_ZOOM_POWER_LIMIT);
  double MICRO_ZOOM_LIMIT = Math.pow(1 / MICRO_ZOOM_RATIO, MICRO_ZOOM_POWER_LIMIT);

  double getZoomFactor();

  void setZoomFactor(double zoomFactor);

  void fitZoomToWindow();

  void zoomOut();

  void zoomIn();

  void setZoomLevelChanged(boolean value);

  boolean canZoomOut();

  boolean canZoomIn();

  boolean isZoomLevelChanged();

  default @Nullable ZoomOptions getCustomZoomOptions() {
    return null;
  }

  default void setCustomZoomOptions(@Nullable ZoomOptions zoomOptions) {
    // Nothing.
  }

  ImageZoomModel STUB = new ImageZoomModel() {
    @Override
    public double getZoomFactor() {
      return 1;
    }

    @Override
    public void setZoomFactor(double zoomFactor) {
    }

    @Override
    public void zoomOut() {
    }

    @Override
    public void zoomIn() {
    }

    @Override
    public void setZoomLevelChanged(boolean value) {
    }

    @Override
    public void fitZoomToWindow() {
    }

    @Override
    public boolean canZoomOut() {
      return false;
    }

    @Override
    public boolean canZoomIn() {
      return false;
    }

    @Override
    public boolean isZoomLevelChanged() {
      return false;
    }
  };
}
