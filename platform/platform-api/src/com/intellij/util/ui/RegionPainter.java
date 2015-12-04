/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * @author Sergey.Malenkov
 */
public interface RegionPainter {
  void paint(Graphics g, int x, int y, int width, int height);

  Key<RegionPainter> BUTTONLESS_SCROLL_BAR_UI_EXTRA_TRACK = Key.create("BUTTONLESS_SCROLL_BAR_UI_EXTRA_TRACK");

  class Image implements RegionPainter {
    private BufferedImage myImage;

    protected void updateImage(BufferedImage image) {
    }

    protected BufferedImage createImage(int width, int height) {
      return UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    protected void invalidate() {
      myImage = null;
    }

    @Override
    public void paint(Graphics g, int x, int y, int width, int height) {
      if (width > 0 && width > 0) {
        if (myImage == null || width != myImage.getWidth() || height != myImage.getHeight()) {
          myImage = createImage(width, height);
        }
        else if (myImage != null) {
          updateImage(myImage);
        }
        if (myImage != null) {
          UIUtil.drawImage(g, myImage, null, x, y);
        }
      }
    }
  }
}
