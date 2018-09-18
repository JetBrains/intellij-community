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
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.ui.ListCellRendererWrapper;

import javax.swing.*;

/**
 * Common {@link ListCellRenderer} do be used in {@link JComboBox} displaying {@link Repository repositories}.
 * We don't want to use {@link Repository#toString()} since it is not the best way to display the repository in the UI.
 *
 * @author Kirill Likhodedov
 */
public class RepositoryComboboxListCellRenderer extends ListCellRendererWrapper<Repository> {

  @Override
  public void customize(JList list, Repository value, int index, boolean selected, boolean hasFocus) {
    setText(DvcsUtil.getShortRepositoryName(value));
  }
}