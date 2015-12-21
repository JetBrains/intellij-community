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
package com.intellij.util;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public interface PlatformIcons {
  @NonNls String PUBLIC_ICON_PATH = "/nodes/c_public.png";
  Icon PUBLIC_ICON = AllIcons.Nodes.C_public;
  Icon LOCKED_ICON = AllIcons.Nodes.Locked;
  Icon SYMLINK_ICON = AllIcons.Nodes.Symlink;
  Icon PRIVATE_ICON = AllIcons.Nodes.C_private;
  Icon PROTECTED_ICON = AllIcons.Nodes.C_protected;
  Icon PACKAGE_LOCAL_ICON = AllIcons.Nodes.C_plocal;
  Icon PACKAGE_ICON = AllIcons.Nodes.Package;

  Icon DIRECTORY_CLOSED_ICON = AllIcons.Nodes.TreeClosed;
  @Deprecated Icon DIRECTORY_OPEN_ICON = DIRECTORY_CLOSED_ICON;

  @NonNls String CLASS_ICON_PATH = "/nodes/class.png";
  Icon CLASS_ICON = AllIcons.Nodes.Class;
  Icon EXCEPTION_CLASS_ICON = AllIcons.Nodes.ExceptionClass;
  Icon NEW_EXCEPTION = AllIcons.Hierarchy.Caller;
  Icon ANONYMOUS_CLASS_ICON = AllIcons.Nodes.AnonymousClass;
  Icon ABSTRACT_CLASS_ICON = AllIcons.Nodes.AbstractClass;
  Icon ANNOTATION_TYPE_ICON = AllIcons.Nodes.Annotationtype;
  Icon ENUM_ICON = AllIcons.Nodes.Enum;
  Icon INTERFACE_ICON = AllIcons.Nodes.Interface;
  Icon METHOD_ICON = AllIcons.Nodes.Method;
  Icon FUNCTION_ICON = AllIcons.Nodes.Function;
  Icon ABSTRACT_METHOD_ICON = AllIcons.Nodes.AbstractMethod;
  Icon FIELD_ICON = AllIcons.Nodes.Field;
  Icon PARAMETER_ICON = AllIcons.Nodes.Parameter;
  Icon VARIABLE_ICON = AllIcons.Nodes.Variable;
  Icon XML_TAG_ICON = AllIcons.Nodes.Tag;
  Icon LIBRARY_ICON = AllIcons.Nodes.PpLib;
  Icon WEB_ICON = AllIcons.Nodes.PpWeb;
  Icon JAR_ICON = AllIcons.Nodes.PpJar;
  Icon FILE_ICON = AllIcons.Nodes.PpFile;

  Icon VARIABLE_READ_ACCESS = AllIcons.Nodes.Read_access;
  Icon VARIABLE_WRITE_ACCESS = AllIcons.Nodes.Write_access;
  Icon VARIABLE_RW_ACCESS = AllIcons.Nodes.Rw_access;
  Icon CUSTOM_FILE_ICON = AllIcons.FileTypes.Custom;
  Icon PROPERTY_ICON = AllIcons.Nodes.Property;
  Icon NEW_PARAMETER = AllIcons.Hierarchy.Caller;
  Icon ASPECT_ICON = AllIcons.Nodes.Aspect;
  Icon ADVICE_ICON = AllIcons.Nodes.Advice;
  Icon ERROR_INTRODUCTION_ICON = AllIcons.Nodes.ErrorIntroduction;
  Icon WARNING_INTRODUCTION_ICON = AllIcons.Nodes.WarningIntroduction;
  Icon JAVA_OUTSIDE_SOURCE_ICON = AllIcons.FileTypes.JavaOutsideSource;
  Icon EXCLUDED_FROM_COMPILE_ICON = AllIcons.Nodes.ExcludedFromCompile;
  Icon PROJECT_ICON = AllIcons.Toolwindows.ToolWindowProject;
  Icon UI_FORM_ICON = AllIcons.FileTypes.UiForm;
  Icon JSP_ICON = AllIcons.FileTypes.Jsp;
  Icon SMALL_VCS_CONFIGURABLE = AllIcons.General.SmallConfigurableVcs;
  Icon GROUP_BY_PACKAGES = AllIcons.Toolbar.Folders;
  Icon ADD_ICON = IconUtil.getAddIcon();
  Icon DELETE_ICON = IconUtil.getRemoveIcon();
  Icon COPY_ICON = AllIcons.Actions.Copy;
  Icon EDIT = IconUtil.getEditIcon();
  Icon ANALYZE = IconUtil.getAnalyzeIcon();
  Icon SELECT_ALL_ICON = AllIcons.Actions.Selectall;
  Icon UNSELECT_ALL_ICON = AllIcons.Actions.Unselectall;
  Icon PROPERTIES_ICON = AllIcons.Actions.Properties;
  Icon SYNCHRONIZE_ICON = AllIcons.Actions.Refresh;
  Icon SHOW_SETTINGS_ICON = AllIcons.General.Settings;

  Icon CHECK_ICON = AllIcons.Actions.Checked;
  Icon CHECK_ICON_SELECTED = AllIcons.Actions.Checked_selected;
  Icon CHECK_ICON_SMALL = AllIcons.Actions.Checked_small;
  Icon CHECK_ICON_SMALL_SELECTED = AllIcons.Actions.Checked_small_selected;

  Icon OPEN_EDIT_DIALOG_ICON = AllIcons.Actions.ShowViewer;
  Icon FLATTEN_PACKAGES_ICON = AllIcons.ObjectBrowser.FlattenPackages;
  Icon EDIT_IN_SECTION_ICON = AllIcons.General.EditItemInSection;
  Icon CLASS_INITIALIZER = AllIcons.Nodes.ClassInitializer;

  Icon CLOSED_MODULE_GROUP_ICON = AllIcons.Nodes.ModuleGroup;
  @Deprecated Icon OPENED_MODULE_GROUP_ICON = CLOSED_MODULE_GROUP_ICON;

  Icon FOLDER_ICON = AllIcons.Nodes.Folder;
  Icon SOURCE_FOLDERS_ICON = AllIcons.Nodes.SourceFolder;
  Icon TEST_SOURCE_FOLDER = AllIcons.Nodes.TestSourceFolder;
  Icon INVALID_ENTRY_ICON = AllIcons.Nodes.PpInvalid;

  Icon MODULES_SOURCE_FOLDERS_ICON = AllIcons.Modules.SourceRoot;
  Icon MODULES_TEST_SOURCE_FOLDER = AllIcons.Modules.TestRoot;

  Icon CONTENT_ROOT_ICON_CLOSED = AllIcons.Nodes.Module;
  @Deprecated Icon CONTENT_ROOT_ICON_OPEN = CONTENT_ROOT_ICON_CLOSED;

  Icon UP_DOWN_ARROWS = AllIcons.Ide.UpDown;

  Icon COMBOBOX_ARROW_ICON = AllIcons.General.ComboArrow;
  
  Icon EXPORT_ICON = AllIcons.ToolbarDecorator.Export;
  Icon IMPORT_ICON = AllIcons.ToolbarDecorator.Import;
}
