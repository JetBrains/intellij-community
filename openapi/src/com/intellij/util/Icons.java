/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.util;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public abstract class Icons {
  public static final Icon PUBLIC_ICON = IconLoader.getIcon("/nodes/c_public.png");
  public static final Icon LOCKED_ICON = IconLoader.getIcon("/nodes/locked.png");
  public static final Icon PRIVATE_ICON = IconLoader.getIcon("/nodes/c_private.png");
  public static final Icon PROTECTED_ICON = IconLoader.getIcon("/nodes/c_protected.png");
  public static final Icon PACKAGE_LOCAL_ICON = IconLoader.getIcon("/nodes/c_plocal.png");
  public static final Icon PACKAGE_ICON = IconLoader.getIcon("/nodes/packageClosed.png");
  public static final Icon PACKAGE_OPEN_ICON = IconLoader.getIcon("/nodes/packageOpen.png");
  public static final Icon DIRECTORY_CLOSED_ICON = IconLoader.getIcon("/nodes/TreeClosed.png");
  public static final Icon DIRECTORY_OPEN_ICON = IconLoader.getIcon("/nodes/TreeOpen.png");
  public static final Icon CLASS_ICON = IconLoader.getIcon("/nodes/class.png");
  public static final Icon EXCEPTION_CLASS_ICON = IconLoader.getIcon("/nodes/exceptionClass.png");
  public static final Icon ANONYMOUS_CLASS_ICON = IconLoader.getIcon("/nodes/anonymousClass.png");
  public static final Icon ABSTRACT_CLASS_ICON = IconLoader.getIcon("/nodes/abstractClass.png");
  public static final Icon JUNIT_TEST_CLASS_ICON = IconLoader.getIcon("/nodes/junitTestClass.png");
  public static final Icon ANNOTATION_TYPE_ICON = IconLoader.getIcon("/nodes/annotationtype.png");
  public static final Icon ENUM_ICON = IconLoader.getIcon("/nodes/enum.png");
  public static final Icon INTERFACE_ICON = IconLoader.getIcon("/nodes/interface.png");
  public static final Icon METHOD_ICON = IconLoader.getIcon("/nodes/method.png");
  public static final Icon ABSTRACT_METHOD_ICON = IconLoader.getIcon("/nodes/abstractMethod.png");
  public static final Icon FIELD_ICON = IconLoader.getIcon("/nodes/field.png");
  public static final Icon PARAMETER_ICON = IconLoader.getIcon("/nodes/parameter.png");
  public static final Icon VARIABLE_ICON = IconLoader.getIcon("/nodes/variable.png");
  public static final Icon XML_TAG_ICON = IconLoader.getIcon("/nodes/tag.png");
  public static final Icon ANT_TARGET_ICON = IconLoader.getIcon("/ant/target.png");
  public static final Icon ANT_META_TARGET_ICON = IconLoader.getIcon("/ant/metaTarget.png");
  public static final Icon LIBRARY_ICON = IconLoader.getIcon("/nodes/ppLib.png");
  public static final Icon WEB_ICON = IconLoader.getIcon("/nodes/ppWeb.png");
  public static final Icon JAR_ICON = IconLoader.getIcon("/nodes/ppJar.png");
  public static final Icon FILE_ICON = IconLoader.getIcon("/nodes/ppFile.png");
  public static final Icon EJB_CLASS_ICON = IconLoader.getIcon("/nodes/ejbClass.png");
  public static final Icon EJB_HOME_INTERFACE_ICON = IconLoader.getIcon("/nodes/ejbHomeClass.png");
  public static final Icon EJB_LOCAL_HOME_INTERFACE_ICON = IconLoader.getIcon("/nodes/ejbHomeLocalClass.png");
  public static final Icon EJB_LOCAL_INTERFACE_ICON = IconLoader.getIcon("/nodes/ejbRemoteLocalClass.png");
  public static final Icon EJB_REMOTE_INTERFACE_ICON = IconLoader.getIcon("/nodes/ejbRemoteClass.png");
  public static final Icon EJB_ICON = IconLoader.getIcon("/nodes/ejb.png");
  public static final Icon EJB_CREATE_METHOD_ICON = IconLoader.getIcon("/nodes/ejbCreateMethod.png");
  public static final Icon EJB_FINDER_METHOD_ICON = IconLoader.getIcon("/nodes/ejbFinderMethod.png");
  public static final Icon EJB_BUSINESS_METHOD_ICON = IconLoader.getIcon("/nodes/ejbBusinessMethod.png");
  public static final Icon EJB_CMP_FIELD_ICON = IconLoader.getIcon("/nodes/ejbCmpField.png");
  public static final Icon EJB_CMR_FIELD_ICON = IconLoader.getIcon("/nodes/ejbCmrField.png");
  public static final Icon EJB_PRIMARY_KEY_CLASS = IconLoader.getIcon("/nodes/ejbPrimaryKeyClass.png");
  public static final Icon EJB_REFERENCE = IconLoader.getIcon("/nodes/ejbReference.png");
  public static final Icon EJB_ENVIRONMENT_ENTRY = IconLoader.getIcon("/nodes/ejbEnvironmentEntry.png");
  public static final Icon EJB_SECURITY_ROLE = IconLoader.getIcon("/nodes/ejbSecurityRole.png");
  public static final Icon EJB_DATASOURCE = IconLoader.getIcon("/nodes/DataSource.png");
  public static final Icon EJB_DATASOURCE_DISABLED = IconLoader.getIcon("/nodes/DataSourceDisabled.png");
  public static final Icon EJB_DATASOURCE_TABLE = IconLoader.getIcon("/nodes/DataTables.png");
  public static final Icon DATASOURCE_REMOTE_INSTANCE = IconLoader.getIcon("/nodes/addRemoteWeblogicInstance.png");
  public static final Icon EJB_FIELD_PK = IconLoader.getIcon("/nodes/fieldPK.png");
  public static final Icon VARIABLE_READ_ACCESS = IconLoader.getIcon("/nodes/read-access.png");
  public static final Icon VARIABLE_WRITE_ACCESS = IconLoader.getIcon("/nodes/write-access.png");
  public static final Icon VARIABLE_RW_ACCESS = IconLoader.getIcon("/nodes/rw-access.png");
  public static final Icon CUSTOM_FILE_ICON = IconLoader.getIcon("/fileTypes/custom.png");
  public static final Icon PROPERTY_ICON = IconLoader.getIcon("/nodes/property.png");
  public static final Icon ASPECT_ICON = IconLoader.getIcon("/nodes/aspect.png");
  public static final Icon POINTCUT_ICON = IconLoader.getIcon("/nodes/pointcut.png");
  public static final Icon ADVICE_ICON = IconLoader.getIcon("/nodes/advice.png");
  public static final Icon ERROR_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/errorIntroduction.png");
  public static final Icon WARNING_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/warningIntroduction.png");
  public static final Icon PARENTS_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/parentsIntroduction.png");
  public static final Icon SOFTENING_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/softeningIntroduction.png");
  public static final Icon JAVA_OUTSIDE_SOURCE_ICON = IconLoader.getIcon("/fileTypes/javaOutsideSource.png");
  public static final Icon EXCLUDED_FROM_COMPILE_ICON = IconLoader.getIcon("/nodes/excludedFromCompile.png");
  public static final Icon PROJECT_ICON = IconLoader.getIcon("/icon_small.png");
  public static final Icon UI_FORM_ICON = IconLoader.getIcon("/fileTypes/uiForm.png");
  public static final Icon JSP_ICON = IconLoader.getIcon("/fileTypes/jsp.png");
  public static final Icon SMALL_VCS_CONFIGURABLE = IconLoader.getIcon("/general/smallConfigurableVcs.png");
  public static final Icon VCS_SMALL_TAB = IconLoader.getIcon("/general/vcsSmallTab.png");
  public static final Icon GROUP_BY_PACKAGES = IconLoader.getIcon("/toolbar/folders.png");
  public static final Icon ADD_ICON = IconLoader.getIcon("/actions/include.png");
  public static final Icon DELETE_ICON = IconLoader.getIcon("/actions/exclude.png");
  public static final Icon OPEN_EDIT_DIALOG_ICON = IconLoader.getIcon("/actions/showViewer.png");
  public static final Icon FLATTEN_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/flattenPackages.png");
}
