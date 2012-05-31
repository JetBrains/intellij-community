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

import com.intellij.icons.AllIcons;

import javax.swing.*;

/**
 * @deprecated use {@link PlatformIcons} for generic icons and corresponding classes from plugins for plugin-specific icons
 * @see PlatformIcons
 */
public abstract class Icons implements PlatformIcons {
  @Deprecated public static final Icon EJB_CLASS_ICON = AllIcons.Javaee.EjbClass;
  @Deprecated public static final Icon EJB_INTERCEPTOR_CLASS_ICON = AllIcons.Javaee.InterceptorClass;
  @Deprecated public static final Icon EJB_HOME_INTERFACE_ICON = AllIcons.Javaee.Home;
  @Deprecated public static final Icon EJB_LOCAL_HOME_INTERFACE_ICON = AllIcons.Javaee.LocalHome;
  @Deprecated public static final Icon EJB_LOCAL_INTERFACE_ICON = AllIcons.Javaee.Local;
  @Deprecated public static final Icon EJB_REMOTE_INTERFACE_ICON = AllIcons.Javaee.Remote;
  @Deprecated public static final Icon EJB_ICON = AllIcons.Nodes.Ejb;
  @Deprecated public static final Icon EJB_CREATE_METHOD_ICON = AllIcons.Nodes.EjbCreateMethod;
  @Deprecated public static final Icon EJB_FINDER_METHOD_ICON = AllIcons.Nodes.EjbFinderMethod;
  @Deprecated public static final Icon EJB_BUSINESS_METHOD_ICON = AllIcons.Nodes.EjbBusinessMethod;
  @Deprecated public static final Icon EJB_CMP_FIELD_ICON = AllIcons.Nodes.EjbCmpField;
  @Deprecated public static final Icon EJB_CMR_FIELD_ICON = AllIcons.Nodes.EjbCmrField;
  @Deprecated public static final Icon EJB_PRIMARY_KEY_CLASS = AllIcons.Nodes.EjbPrimaryKeyClass;
  @Deprecated public static final Icon EJB_REFERENCE = AllIcons.Nodes.EjbReference;
  @Deprecated public static final Icon EJB_FIELD_PK = AllIcons.Nodes.FieldPK;

  @Deprecated public static final Icon DATASOURCE_ICON = AllIcons.Nodes.DataSource;
  @Deprecated public static final Icon DATASOURCE_DISABLED_ICON = AllIcons.Nodes.DataSourceDisabled;
  @Deprecated public static final Icon DATASOURCE_TABLE_ICON = AllIcons.Nodes.DataTables;
  @Deprecated public static final Icon DATASOURCE_VIEW_ICON = AllIcons.Nodes.DataView;
  @Deprecated public static final Icon DATASOURCE_SEQUENCE_ICON = AllIcons.Nodes.DataSequence;
  @Deprecated public static final Icon DATASOURCE_COLUMN_ICON = AllIcons.Nodes.DataColumn;
  @Deprecated public static final Icon DATASOURCE_FK_COLUMN_ICON = AllIcons.Nodes.DataFkColumn;
  @Deprecated public static final Icon DATASOURCE_PK_COLUMN_ICON = AllIcons.Nodes.DataPkColumn;

  @Deprecated public static final Icon WEB_FOLDER_OPEN = AllIcons.Nodes.WebFolderOpen;
  @Deprecated public static final Icon WEB_FOLDER_CLOSED = AllIcons.Nodes.WebFolderClosed;

  @Deprecated public static final Icon DATASOURCE_REMOTE_INSTANCE = AllIcons.Nodes.AddRemoteWeblogicInstance;

  @Deprecated public static final Icon ANT_TARGET_ICON = AllIcons.Ant.Target;
  @Deprecated public static final Icon ANT_META_TARGET_ICON = AllIcons.Ant.MetaTarget;

  @Deprecated public static final Icon JUNIT_TEST_CLASS_ICON = AllIcons.Nodes.JunitTestClass;

  @Deprecated public static final Icon PARENTS_INTRODUCTION_ICON = AllIcons.Nodes.ParentsIntroduction;
  @Deprecated public static final Icon SOFTENING_INTRODUCTION_ICON = AllIcons.Nodes.SofteningIntroduction;
}
