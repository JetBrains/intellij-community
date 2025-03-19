// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.diff;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.Key;
import com.intellij.util.FilePatternFilter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
  private FilePatternFilter myDirDiffFilter;

  public String getFilter() {
    return filter;
  }

  public void setFilter(String filter) {
    this.filter = filter;
    myDirDiffFilter = FilePatternFilter.parseFilter(filter, "&", "|", 0);
  }

  public FilePatternFilter getDirDiffFilter() {
    return myDirDiffFilter;
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

    public @Nls String getPresentableName() {
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
