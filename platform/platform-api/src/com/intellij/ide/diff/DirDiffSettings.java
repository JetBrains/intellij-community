/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.diff;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffSettings {
  public boolean showSize = true;
  public boolean showDate = true;

  public boolean showEqual = false;
  public boolean showDifferent = true;
  public boolean showNewOnSource = true;
  public boolean showNewOnTarget = true;
  public boolean showCompareModes = true;
  public CompareMode compareMode = CompareMode.CONTENT;

  public boolean showInFrame = true; // in dialog otherwise

  //Usually used to set additional compare settings
  private final List<AnAction> extraToolbarActions = new ArrayList<AnAction>();

  //Non-standard diff tools can store additional data here to use it while building data model
  public final HashMap<Object, Object> customSettings = new HashMap<Object, Object>();

  private String filter = "";
  private Pattern filterPattern = PatternUtil.fromMask("*");

  public String getFilter() {
    return filter;
  }

  public void setFilter(String filter) {
    this.filter = filter;
    filterPattern = PatternUtil.fromMask(StringUtil.isEmpty(filter) ? "*" : filter);
  }

  public Pattern getFilterPattern() {
    return filterPattern;
  }

  public static enum CompareMode {
    CONTENT, // the most honest, the slowest. Compares size, if equal compares contents. Ignores timestamps
    SIZE, // Compares size only
    TIMESTAMP; // Compares size, if equal compares timestamps

    public String getPresentableName() {
      return StringUtil.capitalize(name().toLowerCase());
    }
  }

  public <T extends AnAction & DirDiffModelHolder> void addExtraAction(@NotNull T action) {
    extraToolbarActions.add(action);
  }

  public void setModelToExtraActions(DirDiffModel model) {
    for (AnAction action : extraToolbarActions) {
      ((DirDiffModelHolder)action).setModel(model);
    }
  }

  public List<AnAction> getExtraActions() {
    return extraToolbarActions;
  }
}
