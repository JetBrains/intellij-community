/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * A request for a diff or merge operation.
 * @deprecated use {@link com.intellij.diff.requests.DiffRequest} instead
 */
@Deprecated
public abstract class DiffRequest {
  @NonNls private static final String COMMON_DIFF_GROUP_KEY = "DiffWindow";

  private String myGroupKey = COMMON_DIFF_GROUP_KEY;
  @Nullable private final Project myProject;
  private ToolbarAddons myToolbarAddons = ToolbarAddons.NOTHING;
  private Factory<JComponent> myBottomComponentFactory = null;
  private final HashSet myHints = new HashSet();
  private final Map<String, Object> myGenericData;
  private Runnable myOnOkRunnable;
  private final List<Pair<String, DiffRequest>> myAdditional;

  protected DiffRequest(@Nullable Project project) {
    myProject = project;
    myGenericData = new HashMap<>(2);
    myAdditional = new SmartList<>();
  }

  public void setToolbarAddons(@NotNull ToolbarAddons toolbarAddons) {
    myToolbarAddons = toolbarAddons;
    if (haveMultipleLayers()) {
      for (Pair<String, DiffRequest> pair : myAdditional) {
        pair.getSecond().setToolbarAddons(toolbarAddons);
      }
    }
  }

  public String getGroupKey() {
    return myGroupKey;
  }

  public void setGroupKey(@NonNls String groupKey) {
    myGroupKey = groupKey;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public boolean isSafeToCallFromUpdate() {
    return true;
  }
  
  /**
   * @return contents to compare
   */
  @NotNull
  public abstract DiffContent[] getContents();

  public DiffViewerType getType() {
    if (haveMultipleLayers()) return DiffViewerType.multiLayer;
    if (getContentTitles().length == 3) return DiffViewerType.merge;
    return DiffViewerType.contents;
  }

  public boolean haveMultipleLayers() {
    return ! getOtherLayers().isEmpty();
  }

  public void addOtherLayer(final String name, DiffRequest request) {
    myAdditional.add(Pair.create(name, request));
  }

  public List<Pair<String, DiffRequest>> getOtherLayers() {
    return myAdditional;
  }

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   */
  public abstract String[] getContentTitles();

  /**
   * Used as window title
   */
  public abstract String getWindowTitle();

  public void setWindowTitle(final String value) {
    //
  }

  /**
   * <B>Work in progress. Don't rely on this functionality</B><br>
   */
  public void customizeToolbar(DiffToolbar toolbar) {
    myToolbarAddons.customize(toolbar);
  }

  /**
   * <B>Work in progress. Don't rely on this functionality</B><br>
   * @return not null (possibly empty) collection of hints for diff tool.
   */
  public Collection getHints() {
    return Collections.unmodifiableCollection(myHints);
  }

  public void passForDataContext(final DataKey key, final Object value) {
    myGenericData.put(key.getName(), value);
    if (haveMultipleLayers()) {
      for (Pair<String, DiffRequest> pair : myAdditional) {
        pair.getSecond().passForDataContext(key, value);
      }
    }
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
    // do not take hint about no differences acceptable for properties level - then just don't show it
    if (haveMultipleLayers() && ! DiffTool.HINT_ALLOW_NO_DIFFERENCES.equals(hint)) {
      for (Pair<String, DiffRequest> pair : myAdditional) {
        pair.getSecond().addHint(hint);
      }
    }
  }

  /**
   * @param hint
   * @see DiffRequest#getHints()
   */
  public void removeHint(Object hint) {
    myHints.remove(hint);
    if (haveMultipleLayers()) {
      for (Pair<String, DiffRequest> pair : myAdditional) {
        pair.getSecond().removeHint(hint);
      }
    }
  }

  /**
   * <B>Work in progress. Don't rely on this functionality</B><br>
   */
  public interface ToolbarAddons {
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

  public Runnable getOnOkRunnable() {
    return myOnOkRunnable;
  }

  public void setOnOkRunnable(Runnable onOkRunnable) {
    myOnOkRunnable = onOkRunnable;
  }
}
