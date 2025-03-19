// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;
import java.util.function.Function;

public class FormatOnSaveOptionsBase<S extends FormatOnSaveOptionsBase.StateBase> implements PersistentStateComponent<S>, Cloneable {

  protected static final ExtensionPointName<DefaultsProvider>
    EP_NAME = ExtensionPointName.create("com.intellij.formatOnSaveOptions.defaultsProvider");

  /**
   * By default, the 'Reformat code' and 'Optimize imports' check boxes are unchecked on the 'Actions on Save' page, unless implementations
   * of this interface specify a different behavior. In other words, this extension point allows having 'Reformat code' and/or
   * 'Optimize imports' enabled out-of-the-box for the specified file types.
   */
  public interface DefaultsProvider {
    default @NotNull Collection<@NotNull FileType> getFileTypesFormattedOnSaveByDefault() {
      return Collections.emptyList();
    }

    default @NotNull Collection<@NotNull FileType> getFileTypesWithOptimizeImportsOnSaveByDefault() {
      return Collections.emptyList();
    }
  }

  static class StateBase implements Cloneable {
    StateBase(@NotNull Function<? super DefaultsProvider, ? extends Collection<FileType>> enabledByDefaultProvider) {
      for (DefaultsProvider provider : EP_NAME.getExtensionList()) {
        Collection<@NotNull FileType> fileTypes = enabledByDefaultProvider.apply(provider);
        mySelectedFileTypes.addAll(ContainerUtil.map(fileTypes, FileType::getName));
      }

      if (mySelectedFileTypes.isEmpty()) {
        // Default state when there are no extensions that want some files to be processed on save by default.
        myRunOnSave = false;
        myAllFileTypesSelected = true;
      }
      else {
        // Default state when extensions want some files to be precessed on save by default.
        myRunOnSave = true;
        myAllFileTypesSelected = false;
      }
    }

    /**
     * The state of the main 'Run this action on save' check box on the 'Actions on Save' page.
     */
    public boolean myRunOnSave;

    /**
     * If <code>true</code> then {@link #mySelectedFileTypes} doesn't matter. This field set to <code>true</code> guarantees that
     * 'Run this action on save' option remains enabled for all file types even if newly installed plugins contribute new file types (which
     * obviously not yet listed in {@link #mySelectedFileTypes}).
     */
    public boolean myAllFileTypesSelected;

    /**
     * Contains file types (names) that have been individually selected to be processed on save. Makes sense only if
     * {@link #myAllFileTypesSelected} is <code>false</code>.
     */
    public @NotNull SortedSet<@NotNull String> mySelectedFileTypes = new TreeSet<>();

    @Override
    public StateBase clone() {
      try {
        StateBase clone = (StateBase)super.clone();
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
      StateBase state = (StateBase)o;

      return myRunOnSave == state.myRunOnSave &&
             myAllFileTypesSelected == state.myAllFileTypesSelected &&
             mySelectedFileTypes.equals(state.mySelectedFileTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRunOnSave, myAllFileTypesSelected, mySelectedFileTypes);
    }
  }

  private @NotNull S myState;

  public FormatOnSaveOptionsBase(@NotNull S state) {
    myState = state;
  }

  @Override
  public final @NotNull S getState() {
    return myState;
  }

  @Override
  public final void loadState(@NotNull S state) {
    myState = state;
  }

  @Override
  public final void noStateLoaded() {
    convertOldProperties();
  }

  protected void convertOldProperties() {
  }

  public boolean isRunOnSaveEnabled() {
    return myState.myRunOnSave;
  }

  @VisibleForTesting
  public void setRunOnSaveEnabled(boolean enabled) {
    myState.myRunOnSave = enabled;
  }

  public boolean isAllFileTypesSelected() {
    return myState.myAllFileTypesSelected;
  }

  void setRunForAllFileTypes() {
    myState.myAllFileTypesSelected = true;
    myState.mySelectedFileTypes.clear();
  }

  void setRunForSelectedFileTypes(@NotNull Collection<? extends @NotNull FileType> fileTypes) {
    myState.myAllFileTypesSelected = false;
    myState.mySelectedFileTypes.clear();
    for (FileType fileType : fileTypes) {
      myState.mySelectedFileTypes.add(fileType.getName());
    }
  }

  /**
   * Returns {@code true} if either the root `All file types` check box is checked
   * or the root check box is unchecked but the specific `fileType` check box is checked.
   * <br><br>
   * Note that most of the callers should first call {@link #isRunOnSaveEnabled()}.
   * The fact that `All file types` or a specific file type is checked
   * doesn't automatically mean that the feature itself ('Run this action on save') is enabled.
   */
  public boolean isFileTypeSelected(@NotNull FileType fileType) {
    return myState.myAllFileTypesSelected || myState.mySelectedFileTypes.contains(fileType.getName());
  }

  Set<String> getSelectedFileTypes() {
    return new TreeSet<>(myState.mySelectedFileTypes);
  }


  @Override
  public FormatOnSaveOptionsBase<S> clone() {
    try {
      //noinspection unchecked
      FormatOnSaveOptionsBase<S> clone = (FormatOnSaveOptionsBase<S>)super.clone();
      //noinspection unchecked
      clone.myState = (S)myState.clone();
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
    FormatOnSaveOptionsBase<?> base = (FormatOnSaveOptionsBase<?>)o;
    return myState.equals(base.myState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myState);
  }
}
