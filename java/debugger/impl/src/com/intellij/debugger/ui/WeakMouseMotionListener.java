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
package com.intellij.debugger.ui;

import com.intellij.util.WeakListener;

import javax.swing.*;
import java.awt.event.MouseMotionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2004
 */
public class WeakMouseMotionListener extends WeakListener<JComponent, MouseMotionListener> {
  public WeakMouseMotionListener(JComponent source, MouseMotionListener listenerImpl) {
    super(source, MouseMotionListener.class, listenerImpl);
  }
  public void addListener(JComponent source, MouseMotionListener listener) {
    source.addMouseMotionListener(listener);
  }
  public void removeListener(JComponent source, MouseMotionListener listener) {
    source.removeMouseMotionListener(listener);
  }
}
