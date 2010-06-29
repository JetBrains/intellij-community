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
package com.intellij.ui.components;

import com.intellij.ui.ExpandTipHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ExpandTipHandlerFactory;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.EmptyTextHelper;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Collection;

public class JBList extends JList implements ComponentWithEmptyText {
  private EmptyTextHelper myEmptyTextHelper;
  private ExpandTipHandler<Integer> myExpandTipHandler;

  public JBList() {
    init();
  }

  public JBList(ListModel dataModel) {
    super(dataModel);
    init();
  }

  public JBList(Object[] listData) {
    super(listData);
    init();
  }

  public JBList(Collection items) {
    super(items.toArray(new Object[items.size()]));
    init();
  }

  private void init() {
    myEmptyTextHelper = new EmptyTextHelper(this) {
      @Override
      protected boolean isEmpty() {
        return JBList.this.isEmpty();
      }
    };

    myExpandTipHandler = ExpandTipHandlerFactory.install(this);
  }

  public boolean isEmpty() {
    return getItemsCount() == 0;
  }

  public int getItemsCount() {
    ListModel model = getModel();
    return model == null ? 0 : model.getSize();
  }

  public String getEmptyText() {
    return myEmptyTextHelper.getEmptyText();
  }

  public void setEmptyText(String emptyText) {
    myEmptyTextHelper.setEmptyText(emptyText);
  }

  public void setEmptyText(String emptyText, SimpleTextAttributes attrs) {
    myEmptyTextHelper.setEmptyText(emptyText, attrs);
  }

  public void clearEmptyText() {
    myEmptyTextHelper.clearEmptyText();
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs) {
    myEmptyTextHelper.appendEmptyText(text, attrs);
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs, ActionListener listener) {
    myEmptyTextHelper.appendEmptyText(text, attrs, listener);
  }

  public ExpandTipHandler<Integer> getExpandTipHandler() {
    return myExpandTipHandler;
  }
}
