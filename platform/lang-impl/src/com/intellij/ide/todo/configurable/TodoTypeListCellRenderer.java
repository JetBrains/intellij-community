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

package com.intellij.ide.todo.configurable;

import javax.swing.*;
import java.awt.*;

final class TodoTypeListCellRenderer extends DefaultListCellRenderer{
  public TodoTypeListCellRenderer(){
    setHorizontalAlignment(JLabel.CENTER);
  }

  @Override
  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus
  ){
    super.getListCellRendererComponent(list,null,index,isSelected,cellHasFocus);
    Icon attributes=(Icon)value;
    setIcon(attributes);
    return this;
  }
}
