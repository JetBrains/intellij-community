/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class EditorNotificationInfo {

  private String myTitle;
  private Icon myIcon;
  private List<ActionLabelData> myLabelsWithActions = ContainerUtil.newArrayList();

  public EditorNotificationInfo(@NotNull String title,
                                @NotNull ActionLabelData firstLabel,
                                @Nullable ActionLabelData... otherLabels)
  {
    myTitle = title;
    myLabelsWithActions.add(firstLabel);
    if (otherLabels != null) {
      Collections.addAll(myLabelsWithActions, otherLabels);
    }
  }

  public EditorNotificationInfo(@NotNull String title,
                                @NotNull Icon icon,
                                @NotNull ActionLabelData firstLabel,
                                @Nullable ActionLabelData... otherLabels)
  {
    this(title, firstLabel, otherLabels);
    myIcon = icon;
  }

  @NotNull
  public List<ActionLabelData> getLabelAndActions() {
    return myLabelsWithActions;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }


  public static class ActionLabelData {
    public final String label;
    public final Runnable action;
    public boolean updateAllNotificationsOnFinish;

    public ActionLabelData(@NotNull String label, @NotNull Runnable action) {
      this.label = label;
      this.action = action;
    }

    public ActionLabelData setUpdateAllNotificationsOnActionEnd(boolean value) {
      updateAllNotificationsOnFinish = value;
      return this;
    }
  }

}


