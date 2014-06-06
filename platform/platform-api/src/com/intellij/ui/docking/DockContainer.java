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
package com.intellij.ui.docking;

import com.intellij.openapi.Disposable;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ui.update.Activatable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface DockContainer extends Disposable, Activatable {
  enum ContentResponse {ACCEPT_MOVE, ACCEPT_COPY, DENY;
    public boolean canAccept() {return this != DENY;}
  }

  RelativeRectangle getAcceptArea();

  /**
   * This area is used when nothing was found with getAcceptArea 
   */
  RelativeRectangle getAcceptAreaFallback();

  @NotNull
  ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point);

  JComponent getContainerComponent();

  void add(@NotNull DockableContent content, RelativePoint dropTarget);

  /**
   * Closes all contained editors.
   */
  void closeAll();

  void addListener(Listener listener, Disposable parent);

  boolean isEmpty();

  @Nullable
  Image startDropOver(@NotNull DockableContent content, RelativePoint point);

  @Nullable
  Image processDropOver(@NotNull DockableContent content, RelativePoint point);

  void resetDropOver(@NotNull DockableContent content);


  boolean isDisposeWhenEmpty();

  interface Dialog extends DockContainer {}

  interface Persistent extends DockContainer {

    String getDockContainerType();
    Element getState();

  }

  interface Listener {
    void contentAdded(Object key);
    void contentRemoved(Object key);
    
    class Adapter implements Listener {
      @Override
      public void contentAdded(Object key) {
      }

      @Override
      public void contentRemoved(Object key) {
      }
    }
  }
}
