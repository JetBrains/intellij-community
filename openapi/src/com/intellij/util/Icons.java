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


public interface Icons {
  Icon PUBLIC_ICON = IconLoader.getIcon("/nodes/c_public.png");
  Icon LOCKED_ICON = IconLoader.getIcon("/nodes/locked.png");
  Icon PRIVATE_ICON = IconLoader.getIcon("/nodes/c_private.png");
  Icon PROTECTED_ICON = IconLoader.getIcon("/nodes/c_protected.png");
  Icon PACKAGE_LOCAL_ICON = IconLoader.getIcon("/nodes/c_plocal.png");
  Icon PACKAGE_ICON = IconLoader.getIcon("/nodes/packageClosed.png");
  Icon DIRECTORY_CLOSED_ICON = IconLoader.getIcon("/nodes/TreeClosed.png");
  Icon DIRECTORY_OPEN_ICON = IconLoader.getIcon("/nodes/TreeOpen.png");
  Icon CLASS_ICON = IconLoader.getIcon("/nodes/class.png");
  Icon ANNOTATION_TYPE_ICON = IconLoader.getIcon("/nodes/annotationType.png");
  Icon ENUM_ICON = IconLoader.getIcon("/nodes/enum.png");
  Icon INTERFACE_ICON = IconLoader.getIcon("/nodes/interface.png");
  Icon METHOD_ICON = IconLoader.getIcon("/nodes/method.png");
  Icon FIELD_ICON = IconLoader.getIcon("/nodes/field.png");
  Icon PARAMETER_ICON = IconLoader.getIcon("/nodes/parameter.png");
  Icon VARIABLE_ICON = IconLoader.getIcon("/nodes/variable.png");
  Icon XML_TAG_ICON = IconLoader.getIcon("/nodes/tag.png");
  Icon ANT_TARGET_ICON = IconLoader.getIcon("/ant/target.png");
  Icon STATIC_CLASS_ICON = IconLoader.getIcon("/nodes/staticClass.png");
  Icon STATIC_INTERFACE_ICON = IconLoader.getIcon("/nodes/staticInterface.png");
  Icon STATIC_METHOD_ICON = IconLoader.getIcon("/nodes/staticMethod.png");
  Icon STATIC_FIELD_ICON = IconLoader.getIcon("/nodes/staticField.png");
  Icon LIBRARY_ICON = IconLoader.getIcon("/nodes/ppLib.png");
  Icon WEB_ICON = IconLoader.getIcon("/nodes/ppWeb.png");
  Icon JAR_ICON = IconLoader.getIcon("/nodes/ppJar.png");
  Icon FILE_ICON = IconLoader.getIcon("/nodes/ppFile.png");
  Icon EJB_CLASS_ICON = IconLoader.getIcon("/nodes/ejbClass.png");
  Icon EJB_HOME_INTERFACE_ICON = IconLoader.getIcon("/nodes/ejbHomeClass.png");
  Icon EJB_LOCAL_HOME_INTERFACE_ICON = IconLoader.getIcon("/nodes/ejbHomeLocalClass.png");
  Icon EJB_LOCAL_INTERFACE_ICON = IconLoader.getIcon("/nodes/ejbRemoteLocalClass.png");
  Icon EJB_REMOTE_INTERFACE_ICON = IconLoader.getIcon("/nodes/ejbRemoteClass.png");
  Icon EJB_ICON = IconLoader.getIcon("/nodes/ejb.png");
  Icon EJB_CREATE_METHOD_ICON = IconLoader.getIcon("/nodes/ejbCreateMethod.png");
  Icon EJB_FINDER_METHOD_ICON = IconLoader.getIcon("/nodes/ejbFinderMethod.png");
  Icon EJB_BUSINESS_METHOD_ICON = IconLoader.getIcon("/nodes/ejbBusinessMethod.png");
  Icon EJB_CMP_FIELD_ICON = IconLoader.getIcon("/nodes/ejbCmpField.png");
  Icon EJB_CMR_FIELD_ICON = IconLoader.getIcon("/nodes/ejbCmrField.png");
  Icon EJB_PRIMARY_KEY_CLASS = IconLoader.getIcon("/nodes/ejbPrimaryKeyClass.png");
  Icon EJB_REFERENCE = IconLoader.getIcon("/nodes/ejbReference.png");
  Icon EJB_ENVIRONMENT_ENTRY = IconLoader.getIcon("/nodes/ejbEnvironmentEntry.png");
  Icon EJB_SECURITY_ROLE = IconLoader.getIcon("/nodes/ejbSecurityRole.png");
  Icon EJB_DATASOURCE = IconLoader.getIcon("/nodes/DataSource.png");
  Icon EJB_DATASOURCE_DISABLED = IconLoader.getIcon("/nodes/DataSourceDisabled.png");
  Icon EJB_DATASOURCE_ROOT = IconLoader.getIcon("/nodes/DataSource.png");
  Icon EJB_DATASOURCE_TABLE = IconLoader.getIcon("/nodes/DataTables.png");
  Icon DATASOURCE_REMOTE_INSTANCE = IconLoader.getIcon("/nodes/addRemoteWeblogicInstance.png");
  Icon EJB_FIELD_PK = IconLoader.getIcon("/nodes/fieldPK.png");
  Icon VARIABLE_READ_ACCESS = IconLoader.getIcon("/nodes/read-access.png");
  Icon VARIABLE_WRITE_ACCESS = IconLoader.getIcon("/nodes/write-access.png");
  Icon VARIABLE_RW_ACCESS = IconLoader.getIcon("/nodes/rw-access.png");
  Icon CUSTOM_FILE_ICON = IconLoader.getIcon("/fileTypes/custom.png");
  Icon PROPERTY_ICON = IconLoader.getIcon("/nodes/property.png");
  Icon ASPECT_ICON = IconLoader.getIcon("/nodes/aspect.png");
  Icon POINTCUT_ICON = IconLoader.getIcon("/nodes/pointcut.png");
  Icon ADVICE_ICON = IconLoader.getIcon("/nodes/advice.png");
  Icon ERROR_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/errorIntroduction.png");
  Icon WARNING_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/warningIntroduction.png");
  Icon PARENTS_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/parentsIntroduction.png");
  Icon SOFTENING_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/softeningIntroduction.png");
  Icon JAVA_OUTSIDE_SOURCE_ICON = IconLoader.getIcon("/fileTypes/javaOutsideSource.png");
  Icon EXCLUDED_FROM_COMPILE_ICON = IconLoader.getIcon("/nodes/excludedFromCompile.png");
  Icon PROJECT_ICON = IconLoader.getIcon("/icon_small.png");
  Icon UI_FORM_ICON = IconLoader.getIcon("/fileTypes/uiForm.png");
  Icon JSP_ICON = IconLoader.getIcon("/fileTypes/jsp.png");
}
