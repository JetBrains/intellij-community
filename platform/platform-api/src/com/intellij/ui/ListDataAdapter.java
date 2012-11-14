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
package com.intellij.ui;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * @author Dmitry Avdeev
 *         Date: 11/14/12
 */
public class ListDataAdapter implements ListDataListener {
  @Override
  public void intervalAdded(ListDataEvent e) {
    dataChanged(e);
  }

  @Override
  public void intervalRemoved(ListDataEvent e) {
    dataChanged(e);
  }

  @Override
  public void contentsChanged(ListDataEvent e) {
    dataChanged(e);
  }

  protected void dataChanged(ListDataEvent e) {
  }
}
