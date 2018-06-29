// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.NodeOptions;

/**
 * The view settings for the project view.
 */
public interface ViewSettings extends NodeOptions {

  /**
   * The default view settings for the project view.
   */
  ViewSettings DEFAULT = Immutable.DEFAULT;

  /**
   * @return {@code true} if directories (folders or packages) should be separated from files, {@code false} otherwise.
   */
  default boolean isFoldersAlwaysOnTop() {
    return true;
  }

  /**
   * Checks if the project view displays members of classes.
   *
   * @return true if class members are displayed, false otherwise.
   */
  boolean isShowMembers();

  /**
   * Checks if the project view displays the structure view pane.
   *
   * @return true if the structure view is displayed, false otherwise.
   */
  boolean isStructureView();

  /**
   * Checks if modules are shown on the "Packages" tab of the project view.
   *
   * @return true if the modules are shown, false otherwise.
   */
  boolean isShowModules();

  /**
   * @return {@code true} if modules should be shown in a flat list without grouping accordingly to qualified names
   */
  default boolean isFlattenModules() {
    return false;
  }

  /**
   * Checks if the project view displays URL for projects, modules and libraries.
   *
   * @return {@code true} if URL is displayed, {@code false} otherwise.
   */
  default boolean isShowURL() {
    return true;
  }

  class Immutable extends NodeOptions.Immutable implements ViewSettings {
    public static final ViewSettings DEFAULT = new ViewSettings.Immutable(null);

    private final boolean myFoldersAlwaysOnTop;
    private final boolean myShowMembers;
    private final boolean myStructureView;
    private final boolean myShowModules;
    private final boolean myFlattenModules;
    private final boolean myShowURL;

    public Immutable(ViewSettings settings) {
      super(settings);
      myFoldersAlwaysOnTop = settings == null || settings.isFoldersAlwaysOnTop();
      myShowMembers = settings != null && settings.isShowMembers();
      myStructureView = settings != null && settings.isStructureView();
      myShowModules = settings == null || settings.isShowModules();
      myFlattenModules = settings != null && settings.isFlattenModules();
      myShowURL = settings == null || settings.isShowURL();
    }

    @Override
    public boolean isFoldersAlwaysOnTop() {
      return myFoldersAlwaysOnTop;
    }

    @Override
    public boolean isShowMembers() {
      return myShowMembers;
    }

    @Override
    public boolean isStructureView() {
      return myStructureView;
    }

    @Override
    public boolean isShowModules() {
      return myShowModules;
    }

    @Override
    public boolean isFlattenModules() {
      return myFlattenModules;
    }

    @Override
    public boolean isShowURL() {
      return myShowURL;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) return true;
      if (!super.equals(object)) return false;
      ViewSettings settings = (ViewSettings)object;
      return settings.isShowMembers() == isShowMembers() &&
             settings.isStructureView() == isStructureView() &&
             settings.isShowModules() == isShowModules() &&
             settings.isFlattenModules() == isFlattenModules() &&
             settings.isShowURL() == isShowURL();
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Boolean.hashCode(isShowMembers());
      result = 31 * result + Boolean.hashCode(isStructureView());
      result = 31 * result + Boolean.hashCode(isShowModules());
      result = 31 * result + Boolean.hashCode(isFlattenModules());
      result = 31 * result + Boolean.hashCode(isShowURL());
      return result;
    }
  }
}
