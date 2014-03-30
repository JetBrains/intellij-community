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

package com.intellij.execution.util;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EnvVariablesTable extends ListTableWithButtons<EnvironmentVariable> {
  @Override
  protected ListTableModel createListModel() {
    final ColumnInfo name = new ElementsColumnInfoBase<EnvironmentVariable>("Name") {
      @Override
      public String valueOf(EnvironmentVariable environmentVariable) {
        return environmentVariable.getName();
      }

      @Override
      public boolean isCellEditable(EnvironmentVariable environmentVariable) {
        return environmentVariable.getNameIsWriteable();
      }

      @Override
      public void setValue(EnvironmentVariable environmentVariable, String s) {
        if (s.equals(valueOf(environmentVariable))) {
          return;
        }
        environmentVariable.setName(s);
        setModified();
      }

      @Override
      protected String getDescription(EnvironmentVariable environmentVariable) {
        return environmentVariable.getDescription();
      }
    };

    final ColumnInfo value = new ElementsColumnInfoBase<EnvironmentVariable>("Value") {
      @Override
      public String valueOf(EnvironmentVariable environmentVariable) {
        return environmentVariable.getValue();
      }

      @Override
      public boolean isCellEditable(EnvironmentVariable environmentVariable) {
        return !environmentVariable.getIsPredefined();
      }

      @Override
      public void setValue(EnvironmentVariable environmentVariable, String s) {
        if (s.equals(valueOf(environmentVariable))) {
          return;
        }
        environmentVariable.setValue(s);
        setModified();
      }

      @Nullable
      @Override
      protected String getDescription(EnvironmentVariable environmentVariable) {
        return environmentVariable.getDescription();
      }
    };

    return new ListTableModel((new ColumnInfo[]{name, value}));
  }


  public List<EnvironmentVariable> getEnvironmentVariables() {
    return getElements();
  }

  @Override
  protected EnvironmentVariable createElement() {
    return new EnvironmentVariable("", "", false);
  }

  @Override
  protected EnvironmentVariable cloneElement(EnvironmentVariable envVariable) {
    return envVariable.clone();
  }

  @Override
  protected boolean canDeleteElement(EnvironmentVariable selection) {
    return !selection.getIsPredefined();
  }
}
