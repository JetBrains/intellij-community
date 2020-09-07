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
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffSettings {
  public static final Key<DirDiffSettings> KEY = Key.create("Diff.DirDiffSettings");

  public boolean showSize = true;
  public boolean showDate = true;

  public boolean showEqual = false;
  public boolean showDifferent = true;
  public boolean showNewOnSource = true;
  public boolean showNewOnTarget = true;
  public boolean showCompareModes = true;
  public boolean enableChoosers = true;
  /** If {@code true} it's allowed to synchronize the left and the right parts by copying and deleting files directly in the diff viewer */
  public boolean enableOperations = true;
  public boolean enableSyncActions = true;
  public CompareMode compareMode = CompareMode.CONTENT;
  public double compareTimestampAccuracy = 0;
  public CustomSourceChooser customSourceChooser;

  public boolean showInFrame = true; // in dialog otherwise

  //Usually used to set additional compare settings
  private final List<AnAction> extraToolbarActions = new ArrayList<>();

  //Non-standard diff tools can store additional data here to use it while building data model
  public final HashMap<Object, Object> customSettings = new HashMap<>();

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

  public enum CompareMode {
    CONTENT("dirdiff.mode.binary.content"), // the most honest, the slowest. Compares size, if equal compares contents. Ignores timestamps
    TEXT("dirdiff.mode.text"), // compare by text representation (Ignore used charset/line separators).
    SIZE("dirdiff.mode.size"), // Compares size only
    TIMESTAMP("dirdiff.mode.size.and.timestamp"); // Compares size, if equal compares timestamps

    private final String myPresentableKey;

    CompareMode(@PropertyKey(resourceBundle = DiffBundle.BUNDLE) String presentableKey) {
      myPresentableKey = presentableKey;
    }

    @Nls
    public String getPresentableName() {
      return DiffBundle.message(myPresentableKey);
    }
  }

  public <T extends AnAction> void addExtraAction(@NotNull T action) {
    extraToolbarActions.add(action);
  }

  public List<AnAction> getExtraActions() {
    return extraToolbarActions;
  }

  public interface CustomSourceChooser {
    @Nullable
    DiffElement chooseSource(@NotNull DiffElement first, @NotNull DiffElement second);
  }
}
