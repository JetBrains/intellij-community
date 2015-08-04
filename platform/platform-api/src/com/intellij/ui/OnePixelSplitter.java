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
package com.intellij.ui;

import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class OnePixelSplitter extends JBSplitter {
  private Producer<Insets> myBlindZone;

  public OnePixelSplitter() {
    super();
    init();
  }

  public OnePixelSplitter(boolean vertical) {
    super(vertical);
    init();
  }

  public OnePixelSplitter(boolean vertical, @NotNull String proportionKey, float defaultProportion) {
    super(vertical, proportionKey, defaultProportion);
    init();
  }

  public OnePixelSplitter(boolean vertical, float proportion) {
    super(vertical, proportion);
    init();
  }

  public OnePixelSplitter(@NotNull String proportionKey, float defaultProportion) {
    super(proportionKey, defaultProportion);

    init();
  }

  public OnePixelSplitter(float proportion) {
    super(proportion);
    init();
  }

  public OnePixelSplitter(boolean vertical, float proportion, float minProp, float maxProp) {
    super(vertical, proportion, minProp, maxProp);
    init();
  }

  protected void init() {
    setDividerWidth(1);
  }

  @Override
  protected Divider createDivider() {
    return new OnePixelDivider(isVertical(), this);
  }

  public void setBlindZone(Producer<Insets> blindZone) {
    myDivider.setOpaque(blindZone == null);
    myBlindZone = blindZone;
  }

  public Producer<Insets> getBlindZone() {
    return myBlindZone;
  }
}
