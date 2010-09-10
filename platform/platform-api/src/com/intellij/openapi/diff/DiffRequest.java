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
package com.intellij.openapi.diff;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * A request for a diff or merge operation.
 */
public abstract class DiffRequest {
  private String myGroupKey = COMMON_DIFF_GROUP_KEY;
  private final Project myProject;
  private ToolbarAddons myToolbarAddons = ToolbarAddons.NOTHING;
  @NonNls private static final String COMMON_DIFF_GROUP_KEY = "DiffWindow";
  private Factory<JComponent> myBottomComponentFactory = null;
  private final HashSet myHints = new HashSet();
  private final Map<String, Object> myGenericData;

  protected DiffRequest(Project project) {
    myProject = project;
    myGenericData = new HashMap<String, Object>(2);
  }

  public void setToolbarAddons(@NotNull ToolbarAddons toolbarAddons) {
    myToolbarAddons = toolbarAddons;
  }

  public String getGroupKey() { return myGroupKey; }
  public void setGroupKey(@NonNls String groupKey) { myGroupKey = groupKey; }
  public Project getProject() { return myProject; }

  /**
   * @return contents to compare
   */
  public abstract DiffContent[] getContents();

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   */
  public abstract String[] getContentTitles();

  /**
   * Used as window title
   */
  public abstract String getWindowTitle();

  /**
   * <B>Work in progress. Don't rely on this functionality</B><br>
   */
  public void customizeToolbar(DiffToolbar toolbar) {
    myToolbarAddons.customize(toolbar);
  }

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   * @return not null (possibly empty) collection of hints for diff tool.
   */
  public Collection getHints() {
    return Collections.unmodifiableCollection(myHints);
  }

  public void passForDataContext(final DataKey key, final Object value) {
    myGenericData.put(key.getName(), value);
  }

  public Map<String, Object> getGenericData() {
    return myGenericData;
  }

  /**
   * @param hint
   * @see DiffRequest#getHints()
   */
  public void addHint(Object hint) {
    myHints.add(hint);
  }

  /**
   * @param hint
   * @see DiffRequest#getHints()
   */
  public void removeHint(Object hint) {
    myHints.remove(hint);
  }

  /**
   * <B>Work in progress. Don't rely on this functionality</B><br>
   */
  public static interface ToolbarAddons {
    /**
     * Does nothing
     */
    ToolbarAddons NOTHING = new ToolbarAddons() {
      public void customize(DiffToolbar toolbar) {
      }
    };

    /**
     * Removes some of default action to use {@link DiffToolbar} as child of main IDEA frame.
     * Removes actions:<p/>
     * {@link IdeActions#ACTION_COPY}<p/>
     * {@link IdeActions#ACTION_FIND}
     */
    ToolbarAddons IDE_FRAME = new ToolbarAddons() {
      public void customize(DiffToolbar toolbar) {
        toolbar.removeActionById(IdeActions.ACTION_COPY);
        toolbar.removeActionById(IdeActions.ACTION_FIND);
      }
    };

    void customize(DiffToolbar toolbar);
  }

  @Nullable
  public JComponent getBottomComponent() {
    return myBottomComponentFactory == null ? null : myBottomComponentFactory.create();
  }

  public void setBottomComponentFactory(final Factory<JComponent> factory) {
    myBottomComponentFactory = factory;
  }
}
