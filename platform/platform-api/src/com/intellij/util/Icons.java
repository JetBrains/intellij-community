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
package com.intellij.util;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @deprecated use {@link PlatformIcons} for generic icons and corresponding classes from plugins for plugin-specific icons
 * @see PlatformIcons
 */
public abstract class Icons implements PlatformIcons {
  @Deprecated public static final Icon EJB_CLASS_ICON = IconLoader.getIcon("/javaee/ejbClass.png");
  @Deprecated public static final Icon EJB_INTERCEPTOR_CLASS_ICON = IconLoader.getIcon("/javaee/interceptorClass.png");
  @Deprecated public static final Icon EJB_HOME_INTERFACE_ICON = IconLoader.getIcon("/javaee/home.png");
  @Deprecated public static final Icon EJB_LOCAL_HOME_INTERFACE_ICON = IconLoader.getIcon("/javaee/localHome.png");
  @Deprecated public static final Icon EJB_LOCAL_INTERFACE_ICON = IconLoader.getIcon("/javaee/local.png");
  @Deprecated public static final Icon EJB_REMOTE_INTERFACE_ICON = IconLoader.getIcon("/javaee/remote.png");
  @Deprecated public static final Icon EJB_ICON = IconLoader.getIcon("/nodes/ejb.png");
  @Deprecated public static final Icon EJB_CREATE_METHOD_ICON = IconLoader.getIcon("/nodes/ejbCreateMethod.png");
  @Deprecated public static final Icon EJB_FINDER_METHOD_ICON = IconLoader.getIcon("/nodes/ejbFinderMethod.png");
  @Deprecated public static final Icon EJB_BUSINESS_METHOD_ICON = IconLoader.getIcon("/nodes/ejbBusinessMethod.png");
  @Deprecated public static final Icon EJB_CMP_FIELD_ICON = IconLoader.getIcon("/nodes/ejbCmpField.png");
  @Deprecated public static final Icon EJB_CMR_FIELD_ICON = IconLoader.getIcon("/nodes/ejbCmrField.png");
  @Deprecated public static final Icon EJB_PRIMARY_KEY_CLASS = IconLoader.getIcon("/nodes/ejbPrimaryKeyClass.png");
  @Deprecated public static final Icon EJB_REFERENCE = IconLoader.getIcon("/nodes/ejbReference.png");
  @Deprecated public static final Icon EJB_FIELD_PK = IconLoader.getIcon("/nodes/fieldPK.png");

  @Deprecated public static final Icon DATASOURCE_ICON = IconLoader.getIcon("/nodes/DataSource.png");
  @Deprecated public static final Icon DATASOURCE_DISABLED_ICON = IconLoader.getIcon("/nodes/DataSourceDisabled.png");
  @Deprecated public static final Icon DATASOURCE_TABLE_ICON = IconLoader.getIcon("/nodes/DataTables.png");
  @Deprecated public static final Icon DATASOURCE_VIEW_ICON = IconLoader.getIcon("/nodes/dataView.png");
  @Deprecated public static final Icon DATASOURCE_SEQUENCE_ICON = IconLoader.getIcon("/nodes/dataSequence.png");
  @Deprecated public static final Icon DATASOURCE_COLUMN_ICON = IconLoader.getIcon("/nodes/dataColumn.png");
  @Deprecated public static final Icon DATASOURCE_FK_COLUMN_ICON = IconLoader.getIcon("/nodes/dataFkColumn.png");
  @Deprecated public static final Icon DATASOURCE_PK_COLUMN_ICON = IconLoader.getIcon("/nodes/dataPkColumn.png");

  @Deprecated public static final Icon WEB_FOLDER_OPEN = IconLoader.getIcon("/nodes/webFolderOpen.png");
  @Deprecated public static final Icon WEB_FOLDER_CLOSED = IconLoader.getIcon("/nodes/webFolderClosed.png");

  @Deprecated public static final Icon DATASOURCE_REMOTE_INSTANCE = IconLoader.getIcon("/nodes/addRemoteWeblogicInstance.png");

  @Deprecated public static final Icon ANT_TARGET_ICON = IconLoader.getIcon("/ant/target.png");
  @Deprecated public static final Icon ANT_META_TARGET_ICON = IconLoader.getIcon("/ant/metaTarget.png");

  @Deprecated public static final Icon JUNIT_TEST_CLASS_ICON = IconLoader.getIcon("/nodes/junitTestClass.png");

  @Deprecated public static final Icon PARENTS_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/parentsIntroduction.png");
  @Deprecated public static final Icon SOFTENING_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/softeningIntroduction.png");
}
