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
package com.intellij.ui;

import com.intellij.openapi.components.ServiceManager;

import javax.swing.*;

public abstract class ToolTipHandlerFactory {
  public static ToolTipHandlerFactory getInstance() {
    return ServiceManager.getService(ToolTipHandlerFactory.class);
  }

  public abstract ToolTipHandler<Integer> install(JList list);
  public abstract ToolTipHandler<Integer> install(JTree tree);
  public abstract ToolTipHandler<TableCell> install(JTable table);
}