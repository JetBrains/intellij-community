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
package com.intellij.util;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public interface PlatformIcons {
  @NonNls String PUBLIC_ICON_PATH = "/nodes/c_public.png";
  Icon PUBLIC_ICON = IconLoader.getIcon(PUBLIC_ICON_PATH);
  Icon LOCKED_ICON = IconLoader.getIcon("/nodes/locked.png");
  Icon PRIVATE_ICON = IconLoader.getIcon("/nodes/c_private.png");
  Icon PROTECTED_ICON = IconLoader.getIcon("/nodes/c_protected.png");
  Icon PACKAGE_LOCAL_ICON = IconLoader.getIcon("/nodes/c_plocal.png");
  Icon PACKAGE_ICON = IconLoader.getIcon("/nodes/packageClosed.png");
  Icon PACKAGE_OPEN_ICON = IconLoader.getIcon("/nodes/packageOpen.png");
  Icon DIRECTORY_CLOSED_ICON = IconLoader.getIcon("/nodes/TreeClosed.png");
  Icon DIRECTORY_OPEN_ICON = IconLoader.getIcon("/nodes/TreeOpen.png");
  @NonNls String CLASS_ICON_PATH = "/nodes/class.png";
  Icon CLASS_ICON = IconLoader.getIcon(CLASS_ICON_PATH);
  Icon EXCEPTION_CLASS_ICON = IconLoader.getIcon("/nodes/exceptionClass.png");
  Icon NEW_EXCEPTION = IconLoader.getIcon("/nodes/newException.png");
  Icon ANONYMOUS_CLASS_ICON = IconLoader.getIcon("/nodes/anonymousClass.png");
  Icon ABSTRACT_CLASS_ICON = IconLoader.getIcon("/nodes/abstractClass.png");
  Icon ANNOTATION_TYPE_ICON = IconLoader.getIcon("/nodes/annotationtype.png");
  Icon ENUM_ICON = IconLoader.getIcon("/nodes/enum.png");
  Icon INTERFACE_ICON = IconLoader.getIcon("/nodes/interface.png");
  Icon METHOD_ICON = IconLoader.getIcon("/nodes/method.png");
  Icon FUNCTION_ICON = IconLoader.getIcon("/nodes/function.png");
  Icon ABSTRACT_METHOD_ICON = IconLoader.getIcon("/nodes/abstractMethod.png");
  Icon FIELD_ICON = IconLoader.getIcon("/nodes/field.png");
  Icon PARAMETER_ICON = IconLoader.getIcon("/nodes/parameter.png");
  Icon VARIABLE_ICON = IconLoader.getIcon("/nodes/variable.png");
  Icon XML_TAG_ICON = IconLoader.getIcon("/nodes/tag.png");
  Icon LIBRARY_ICON = IconLoader.getIcon("/nodes/ppLib.png");
  Icon WEB_ICON = IconLoader.getIcon("/nodes/ppWeb.png");
  Icon JAR_ICON = IconLoader.getIcon("/nodes/ppJar.png");
  Icon FILE_ICON = IconLoader.getIcon("/nodes/ppFile.png");

  Icon VARIABLE_READ_ACCESS = IconLoader.getIcon("/nodes/read-access.png");
  Icon VARIABLE_WRITE_ACCESS = IconLoader.getIcon("/nodes/write-access.png");
  Icon VARIABLE_RW_ACCESS = IconLoader.getIcon("/nodes/rw-access.png");
  Icon CUSTOM_FILE_ICON = IconLoader.getIcon("/fileTypes/custom.png");
  Icon PROPERTY_ICON = IconLoader.getIcon("/nodes/property.png");
  Icon NEW_PARAMETER = IconLoader.getIcon("/nodes/newParameter.png");
  Icon ASPECT_ICON = IconLoader.getIcon("/nodes/aspect.png");
  Icon POINTCUT_ICON = IconLoader.getIcon("/nodes/pointcut.png");
  Icon ADVICE_ICON = IconLoader.getIcon("/nodes/advice.png");
  Icon ERROR_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/errorIntroduction.png");
  Icon WARNING_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/warningIntroduction.png");
  Icon JAVA_OUTSIDE_SOURCE_ICON = IconLoader.getIcon("/fileTypes/javaOutsideSource.png");
  Icon EXCLUDED_FROM_COMPILE_ICON = IconLoader.getIcon("/nodes/excludedFromCompile.png");
  Icon PROJECT_ICON = IconLoader.getIcon("/icon_small.png");
  Icon UI_FORM_ICON = IconLoader.getIcon("/fileTypes/uiForm.png");
  Icon JSP_ICON = IconLoader.getIcon("/fileTypes/jsp.png");
  Icon SMALL_VCS_CONFIGURABLE = IconLoader.getIcon("/general/smallConfigurableVcs.png");
  Icon VCS_SMALL_TAB = IconLoader.getIcon("/general/vcsSmallTab.png");
  Icon GROUP_BY_PACKAGES = IconLoader.getIcon("/toolbar/folders.png");
  Icon ADD_ICON = IconLoader.getIcon("/actions/include.png");
  Icon DELETE_ICON = IconLoader.getIcon("/actions/exclude.png");
  Icon MOVE_UP_ICON = IconLoader.getIcon("/actions/moveUp.png");
  Icon MOVE_DOWN_ICON = IconLoader.getIcon("/actions/moveDown.png");
  Icon EDIT = IconLoader.getIcon("/actions/edit.png");
  Icon DUPLICATE_ICON = IconLoader.getIcon("/general/copy.png");
  Icon SELECT_ALL_ICON = IconLoader.getIcon("/actions/selectall.png");
  Icon UNSELECT_ALL_ICON = IconLoader.getIcon("/actions/unselectall.png");
  Icon PROPERTIES_ICON = IconLoader.getIcon("/actions/properties.png");
  Icon SYNCHRONIZE_ICON = IconLoader.getIcon("/actions/sync.png");

  Icon CHECK_ICON = IconLoader.getIcon("/actions/checked.png");
  Icon CHECK_ICON_SELECTED = IconLoader.getIcon("/actions/checked_selected.png");
  Icon CHECK_ICON_SMALL = IconLoader.getIcon("/actions/checked_small.png");
  Icon CHECK_ICON_SMALL_SELECTED = IconLoader.getIcon("/actions/checked_small_selected.png");

  Icon OPEN_EDIT_DIALOG_ICON = IconLoader.getIcon("/actions/showViewer.png");
  Icon FLATTEN_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/flattenPackages.png");
  Icon ADD_TO_SECTION_ICON = IconLoader.getIcon("/general/addItemToSection.png");
  Icon EDIT_IN_SECTION_ICON = IconLoader.getIcon("/general/editItemInSection.png");
  Icon TASK_ICON = IconLoader.getIcon("/ant/task.png");
  Icon CLASS_INITIALIZER = IconLoader.getIcon("/nodes/classInitializer.png");

  Icon OPENED_MODULE_GROUP_ICON = IconLoader.getIcon("/nodes/moduleGroupOpen.png");
  Icon CLOSED_MODULE_GROUP_ICON = IconLoader.getIcon("/nodes/moduleGroupClosed.png");
  Icon FOLDER_ICON = IconLoader.getIcon("/nodes/folder.png");
  Icon SOURCE_FOLDERS_ICON = IconLoader.getIcon("/nodes/sourceFolder.png");
  Icon TEST_SOURCE_FOLDER = IconLoader.getIcon("/nodes/testSourceFolder.png");

  Icon MODULES_SOURCE_FOLDERS_ICON = IconLoader.getIcon("/modules/sourceRootClosed.png");
  Icon MODULES_TEST_SOURCE_FOLDER = IconLoader.getIcon("/modules/testRootClosed.png");

  Icon CONTENT_ROOT_ICON_OPEN = IconLoader.getIcon("/nodes/ModuleOpen.png");
  Icon CONTENT_ROOT_ICON_CLOSED = IconLoader.getIcon("/nodes/ModuleClosed.png");

  Icon UP_DOWN_ARROWS = IconLoader.getIcon("/ide/upDown.png");

  Icon ADD_BIG = IconLoader.getIcon("/actions/addBig.png");
  Icon REMOVE_BIG = IconLoader.getIcon("/actions/removeBig.png");
  Icon UP_BIG = IconLoader.getIcon("/actions/upBig.png");
  Icon DOWN_BIG = IconLoader.getIcon("/actions/downBig.png");

  Icon TABLE_ADD_ROW = IconLoader.getIcon("/tables/add.png");
  Icon TABLE_REMOVE_ROW = IconLoader.getIcon("/tables/remove.png");
  Icon TABLE_MOVE_ROW_UP = IconLoader.getIcon("/tables/moveUp.png");
  Icon TABLE_MOVE_ROW_DOWN = IconLoader.getIcon("/tables/moveDown.png");
  Icon TABLE_EXCEPTION = IconLoader.getIcon("/tables/exception.png");
  Icon TABLE_EDIT_ROW = IconLoader.getIcon("/tables/edit.png");
  Icon TABLE_ANALYZE = IconLoader.getIcon("/tables/analyze.png");

  Icon COMBOBOX_ARROW_ICON = IconLoader.getIcon("/general/comboArrow.png");
}
