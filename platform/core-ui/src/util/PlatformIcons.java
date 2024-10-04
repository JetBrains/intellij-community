// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.icons.AllIcons;
import com.intellij.ui.IconManager;

import javax.swing.*;

public interface PlatformIcons {
  Icon PUBLIC_ICON = AllIcons.Nodes.C_public;
  Icon LOCKED_ICON = AllIcons.Nodes.Locked;
  Icon SYMLINK_ICON = AllIcons.Nodes.Symlink;
  Icon PRIVATE_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Private);
  Icon PROTECTED_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Protected);
  Icon PACKAGE_LOCAL_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Local);
  Icon PACKAGE_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Package);

  Icon CLASS_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class);
  Icon EXCEPTION_CLASS_ICON = AllIcons.Nodes.ExceptionClass;
  Icon ANONYMOUS_CLASS_ICON = AllIcons.Nodes.AnonymousClass;
  Icon ABSTRACT_CLASS_ICON = AllIcons.Nodes.AbstractClass;
  Icon ANNOTATION_TYPE_ICON = AllIcons.Nodes.Annotationtype;
  Icon ENUM_ICON = AllIcons.Nodes.Enum;
  Icon RECORD_ICON = AllIcons.Nodes.Record;
  Icon INTERFACE_ICON = AllIcons.Nodes.Interface;
  Icon METHOD_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method);
  Icon FUNCTION_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Function);
  Icon ABSTRACT_METHOD_ICON = AllIcons.Nodes.AbstractMethod;
  Icon FIELD_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Field);
  Icon PARAMETER_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter);
  Icon VARIABLE_ICON = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Variable);
  Icon XML_TAG_ICON = AllIcons.Nodes.Tag;
  Icon LIBRARY_ICON = AllIcons.Nodes.PpLib;
  Icon WEB_ICON = AllIcons.Nodes.PpWeb;
  Icon JAR_ICON = AllIcons.Nodes.PpJar;
  Icon FILE_ICON = AllIcons.Nodes.Folder;

  Icon VARIABLE_READ_ACCESS = AllIcons.Nodes.ReadAccess;
  Icon VARIABLE_WRITE_ACCESS = AllIcons.Nodes.WriteAccess;
  Icon VARIABLE_RW_ACCESS = AllIcons.Nodes.RwAccess;
  Icon CUSTOM_FILE_ICON = AllIcons.FileTypes.Custom;
  Icon PROPERTY_ICON = AllIcons.Nodes.Property;
  Icon NEW_PARAMETER = AllIcons.Hierarchy.Supertypes;
  Icon ASPECT_ICON = AllIcons.Nodes.Aspect;

  Icon ERROR_INTRODUCTION_ICON = AllIcons.Nodes.ErrorIntroduction;
  Icon WARNING_INTRODUCTION_ICON = AllIcons.Nodes.WarningIntroduction;
  Icon JAVA_OUTSIDE_SOURCE_ICON = AllIcons.FileTypes.JavaOutsideSource;
  Icon EXCLUDED_FROM_COMPILE_ICON = AllIcons.Nodes.ExcludedFromCompile;
  Icon PROJECT_ICON = AllIcons.Toolwindows.ToolWindowProject;
  Icon UI_FORM_ICON = AllIcons.FileTypes.UiForm;
  Icon JSP_ICON = AllIcons.FileTypes.Jsp;
  Icon SMALL_VCS_CONFIGURABLE = AllIcons.Actions.ShowAsTree;
  Icon GROUP_BY_PACKAGES = AllIcons.Actions.GroupByPackage;
  Icon ADD_ICON = AllIcons.General.Add;
  Icon DELETE_ICON = AllIcons.General.Remove;
  Icon COPY_ICON = AllIcons.Actions.Copy;
  Icon EDIT = AllIcons.Actions.Edit;
  Icon SELECT_ALL_ICON = AllIcons.Actions.Selectall;
  Icon UNSELECT_ALL_ICON = AllIcons.Actions.Unselectall;
  Icon PROPERTIES_ICON = AllIcons.Actions.Properties;
  Icon SYNCHRONIZE_ICON = AllIcons.Actions.Refresh;
  Icon SHOW_SETTINGS_ICON = AllIcons.General.Settings;

  Icon CHECK_ICON = AllIcons.Actions.Checked;
  Icon CHECK_ICON_SELECTED = AllIcons.Actions.Checked_selected;
  Icon CHECK_ICON_SMALL = AllIcons.Actions.Checked;
  Icon CHECK_ICON_SMALL_SELECTED = AllIcons.Actions.Checked_selected;

  Icon FLATTEN_PACKAGES_ICON = AllIcons.ObjectBrowser.FlattenPackages;
  Icon EDIT_IN_SECTION_ICON = AllIcons.Actions.Edit;
  Icon CLASS_INITIALIZER = AllIcons.Nodes.ClassInitializer;

  Icon CLOSED_MODULE_GROUP_ICON = AllIcons.Nodes.ModuleGroup;

  Icon FOLDER_ICON = AllIcons.Nodes.Folder;
  Icon SOURCE_FOLDERS_ICON = AllIcons.Nodes.Package;
  Icon TEST_SOURCE_FOLDER = AllIcons.Nodes.TestSourceFolder;
  Icon INVALID_ENTRY_ICON = AllIcons.Nodes.PpInvalid;

  Icon MODULES_SOURCE_FOLDERS_ICON = AllIcons.Modules.SourceRoot;
  Icon MODULES_TEST_SOURCE_FOLDER = AllIcons.Modules.TestRoot;

  Icon CONTENT_ROOT_ICON_CLOSED = AllIcons.Nodes.Module;

  Icon UP_DOWN_ARROWS = AllIcons.Ide.UpDown;

  Icon COMBOBOX_ARROW_ICON = AllIcons.General.ArrowDown;

  Icon EXPORT_ICON = AllIcons.ToolbarDecorator.Export;
  Icon IMPORT_ICON = AllIcons.ToolbarDecorator.Import;

  /** @deprecated use {@link #FOLDER_ICON} */
  @Deprecated(forRemoval = true)
  Icon DIRECTORY_CLOSED_ICON = FOLDER_ICON;
}
