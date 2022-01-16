// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Service
@State(name = "FormatOnSaveOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class FormatOnSaveOptions implements PersistentStateComponent<FormatOnSaveOptions.State>, Cloneable {

  public static @NotNull FormatOnSaveOptions getInstance(@NotNull Project project) {
    return project.getService(FormatOnSaveOptions.class);
  }

  private static final ExtensionPointName<DefaultsProvider>
    EP_NAME = ExtensionPointName.create("com.intellij.formatOnSaveOptions.defaultsProvider");

  public interface DefaultsProvider {
    @NotNull Collection<@NotNull FileType> getFileTypesFormattedOnSaveByDefault();
  }

  static class State implements Cloneable {
    /**
     * The state of the main 'Format on save' check box on the 'Actions on Save' page.
     */
    public boolean myFormatOnSave;

    /**
     * Makes sense only in VCS is used in the project ({@link VcsFacade#hasActiveVcss(Project)} is <code>true</code>).
     */
    public boolean myFormatOnlyChangedLines;

    /**
     * If <code>true</code> then {@link #mySelectedFileTypes} doesn't matter. This field set to <code>true</code> guarantees that
     * 'Format on save' option remains enabled for all file types even if newly installed plugins contribute new file types (which
     * obviously not yet listed in {@link #mySelectedFileTypes}).
     */
    public boolean myAllFileTypesSelected;

    /**
     * Contains file types (names) that have been individually selected to be formatted on save. Makes sense only if
     * {@link #myAllFileTypesSelected} is <code>false</code>.
     */
    public @NotNull SortedSet<@NotNull String> mySelectedFileTypes = new TreeSet<>();

    State() {
      for (DefaultsProvider provider : EP_NAME.getExtensionList()) {
        mySelectedFileTypes.addAll(ContainerUtil.map(provider.getFileTypesFormattedOnSaveByDefault(), FileType::getName));
      }

      if (mySelectedFileTypes.isEmpty()) {
        // Default state when there are no extensions that want some files to be formatted on save by default.
        myFormatOnSave = false;
        myAllFileTypesSelected = true;
      }
      else {
        // Default state when extensions want some files to be formatted on save by default.
        myFormatOnSave = true;
        myAllFileTypesSelected = false;
      }
    }

    @Override
    public State clone() {
      try {
        State clone = (State)super.clone();
        clone.mySelectedFileTypes = new TreeSet<>(mySelectedFileTypes);
        return clone;
      }
      catch (CloneNotSupportedException e) {
        throw new AssertionError();
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      State state = (State)o;

      return myFormatOnSave == state.myFormatOnSave &&
             myFormatOnlyChangedLines == state.myFormatOnlyChangedLines &&
             myAllFileTypesSelected == state.myAllFileTypesSelected &&
             mySelectedFileTypes.equals(state.mySelectedFileTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFormatOnSave, myFormatOnlyChangedLines, myAllFileTypesSelected, mySelectedFileTypes);
    }
  }

  private final @NotNull Project myProject;
  private @NotNull State myState = new State();

  public FormatOnSaveOptions(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull FormatOnSaveOptions.State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull FormatOnSaveOptions.State state) {
    myState = state;
  }

  @Override
  public void noStateLoaded() {
    convertOldProperties();
  }

  private void convertOldProperties() {
    String oldFormatOnSaveProperty = "format.on.save";
    boolean formatAllOld = PropertiesComponent.getInstance(myProject).getBoolean(oldFormatOnSaveProperty);
    if (formatAllOld) {
      myState.myFormatOnSave = true;
      myState.myAllFileTypesSelected = true;
      myState.mySelectedFileTypes.clear();
    }
    PropertiesComponent.getInstance(myProject).unsetValue(oldFormatOnSaveProperty);

    String oldOnlyChangedLinesProperty = "format.on.save.only.changed.lines";
    boolean onlyChangedLinesOld = PropertiesComponent.getInstance(myProject).getBoolean(oldOnlyChangedLinesProperty);
    if (onlyChangedLinesOld) {
      myState.myFormatOnlyChangedLines = true;
    }
    PropertiesComponent.getInstance(myProject).unsetValue(oldOnlyChangedLinesProperty);
  }

  public boolean isFormatOnSaveEnabled() {
    return myState.myFormatOnSave;
  }

  void setFormatOnSaveEnabled(boolean enabled) {
    myState.myFormatOnSave = enabled;
  }

  public boolean isFormatOnlyChangedLines() {
    return myState.myFormatOnlyChangedLines;
  }

  void setFormatOnlyChangedLines(boolean changedLines) {
    myState.myFormatOnlyChangedLines = changedLines;
  }

  public boolean isAllFileTypesSelected() {
    return myState.myAllFileTypesSelected;
  }

  public void setAllFileTypesSelected(boolean all) {
    myState.myAllFileTypesSelected = all;
    myState.mySelectedFileTypes.clear();
  }

  /**
   * File type may be selected in the popup on the 'Actions on Save' page even if the main 'Format on save' checkbox is not selected.
   * Most of the callers should first check {@link #isFormatOnSaveEnabled()}.
   */
  public boolean isFileTypeSelected(@NotNull FileType fileType) {
    return myState.myAllFileTypesSelected || myState.mySelectedFileTypes.contains(fileType.getName());
  }

  void setFileTypeSelected(@NotNull FileType fileType, boolean selected) {
    if (selected) {
      myState.mySelectedFileTypes.add(fileType.getName());
    }
    else {
      myState.mySelectedFileTypes.remove(fileType.getName());
    }
  }

  Set<String> getSelectedFileTypes() {
    return new TreeSet<>(myState.mySelectedFileTypes);
  }

  @Override
  public FormatOnSaveOptions clone() {
    try {
      FormatOnSaveOptions clone = (FormatOnSaveOptions)super.clone();
      clone.myState = myState.clone();
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FormatOnSaveOptions options = (FormatOnSaveOptions)o;
    return myProject.equals(options.myProject) && myState.equals(options.myState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myProject, myState);
  }
}
