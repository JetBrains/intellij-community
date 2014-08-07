/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author Konstantin Bulenkov
 */
public class OnePixelSplitter extends JBSplitter {

  public OnePixelSplitter() {
    super();
    init();
  }

  public OnePixelSplitter(boolean vertical) {
    super(vertical);
    init();
  }

  public OnePixelSplitter(boolean vertical, float proportion) {
    super(vertical, proportion);
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
}
