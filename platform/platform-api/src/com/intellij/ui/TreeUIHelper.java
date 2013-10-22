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
package com.intellij.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.Convertor;

import javax.swing.*;
import javax.swing.tree.TreePath;

/**
 * @author yole
 */
public abstract class TreeUIHelper {
  public static TreeUIHelper getInstance() {
    return ServiceManager.getService(TreeUIHelper.class);
  }

  /**
   * @deprecated use JBTree class instead, it will automatically configure tool tips 
   */
  public abstract void installToolTipHandler(JTree tree);
  /**
   * @deprecated use JBTable class instead, it will automatically configure tool tips
   */
  public abstract void installToolTipHandler(JTable table);
  /**
   * @deprecated use JBLIst class instead, it will automatically configure tool tips 
   */
  public abstract void installToolTipHandler(JList list);

  public abstract void installEditSourceOnDoubleClick(JTree tree);

  public abstract void installTreeSpeedSearch(JTree tree);
  public abstract void installListSpeedSearch(JList list);
  public abstract void installTreeSpeedSearch(JTree tree, Convertor<TreePath, String> convertor, boolean canExpand);
  public abstract void installListSpeedSearch(JList list, Convertor<Object, String> convertor);

  public abstract void installEditSourceOnEnterKeyHandler(JTree tree);

  public abstract void installSmartExpander(JTree tree);

  public abstract void installSelectionSaver(JTree tree);
}