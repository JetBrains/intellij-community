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

/*
 * User: anna
 * Date: 12-Nov-2007
 */
package com.intellij.openapi.wm;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;

import javax.swing.*;

public abstract class IdeRootPaneNorthExtension implements Disposable {
  public static final ExtensionPointName<IdeRootPaneNorthExtension> EP_NAME = ExtensionPointName.create("com.intellij.ideRootPaneNorth");

  public abstract String getKey();

  public abstract JComponent getComponent();

  public abstract void uiSettingsChanged(UISettings settings);

  public abstract IdeRootPaneNorthExtension copy();
  
  public void revalidate(){}
}
