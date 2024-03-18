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
package org.intellij.images.options;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * Options for zooming feature.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ZoomOptions extends Cloneable {
  @NonNls
  String ATTR_PREFIX = "Editor.Zoom.";
  @NonNls
  String ATTR_PREFFERED_WIDTH = ATTR_PREFIX + "prefferedWidth";
  @NonNls
  String ATTR_PREFFERED_HEIGHT = ATTR_PREFIX + "prefferedHeight";

  Dimension DEFAULT_PREFFERED_SIZE = new Dimension(128, 128);

  default boolean isWheelZooming() {
    return Registry.is("ide.images.wheel.zooming", true);
  }

  default boolean isSmartZooming() {
    return isWheelZooming();
  }

  default Double getSmartZoomFactor(Rectangle imageSize, Dimension viewPort, int inset) {
    if (imageSize == null) return null;
    if (imageSize.getWidth() == 0 || imageSize.getHeight() == 0) return null;
    int width = imageSize.width;
    int height = imageSize.height;

    Dimension preferredMinimumSize = getPrefferedSize();
    if (width < preferredMinimumSize.width &&
        height < preferredMinimumSize.height) {
      double factor = (preferredMinimumSize.getWidth() / (double)width +
                       preferredMinimumSize.getHeight() / (double)height) / 2.0d;
      return Math.ceil(factor);
    }

    viewPort.height -= inset * 2;
    viewPort.width -= inset * 2;
    if (viewPort.width <= 0 || viewPort.height <= 0) return null;

    if (viewPort.width < width || viewPort.height < height) {
      return Math.min((double)viewPort.height / height,
                      (double)viewPort.width / width);
    }

    return 1.0d;
  }

  Dimension getPrefferedSize();

  void inject(ZoomOptions options);

  boolean setOption(String name, Object value);
}
