// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

import static com.intellij.util.ObjectUtils.notNull;

public final class JavaTemplateUtil {
  public static final String TEMPLATE_CATCH_BODY = "Catch Statement Body.java";
  public static final String TEMPLATE_CATCH_DECLARATION = "Catch Statement Declaration.java";
  public static final String TEMPLATE_SWITCH_DEFAULT_BRANCH = "Switch Default Branch.java";
  public static final String TEMPLATE_IMPLEMENTED_METHOD_BODY = "Implemented Method Body.java";
  public static final String TEMPLATE_OVERRIDDEN_METHOD_BODY = "Overridden Method Body.java";
  public static final String TEMPLATE_FROM_USAGE_METHOD_BODY = "New Method Body.java";
  public static final String TEMPLATE_I18NIZED_EXPRESSION = "I18nized Expression.java";
  public static final String TEMPLATE_I18NIZED_CONCATENATION = "I18nized Concatenation.java";
  public static final String TEMPLATE_I18NIZED_JSP_EXPRESSION = "I18nized JSP Expression.jsp";
  public static final String TEMPLATE_JAVADOC_CLASS = "JavaDoc Class.java";
  public static final String TEMPLATE_JAVADOC_FIELD = "JavaDoc Field.java";
  public static final String TEMPLATE_JAVADOC_CONSTRUCTOR = "JavaDoc Constructor.java";
  public static final String TEMPLATE_JAVADOC_METHOD = "JavaDoc Method.java";
  public static final String TEMPLATE_JAVADOC_OVERRIDING_METHOD = "JavaDoc Overriding Method.java";

  public static final String INTERNAL_CLASS_TEMPLATE_NAME = "Class";
  public static final String INTERNAL_INTERFACE_TEMPLATE_NAME = "Interface";
  public static final String INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME = "AnnotationType";
  public static final String INTERNAL_EXCEPTION_TYPE_TEMPLATE_NAME = "Exception";
  public static final String INTERNAL_ENUM_TEMPLATE_NAME = "Enum";
  public static final String INTERNAL_RECORD_TEMPLATE_NAME = "Record";
  public static final String INTERNAL_SIMPLE_SOURCE_FILE = "SimpleSourceFile";

  public static final String[] INTERNAL_CLASS_TEMPLATES = {
    INTERNAL_CLASS_TEMPLATE_NAME, INTERNAL_INTERFACE_TEMPLATE_NAME, INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME, INTERNAL_ENUM_TEMPLATE_NAME,
    INTERNAL_RECORD_TEMPLATE_NAME, INTERNAL_EXCEPTION_TYPE_TEMPLATE_NAME, INTERNAL_SIMPLE_SOURCE_FILE};

  public static final String INTERNAL_PACKAGE_INFO_TEMPLATE_NAME = "package-info";
  public static final String INTERNAL_MODULE_INFO_TEMPLATE_NAME = "module-info";

  public static final String[] INTERNAL_FILE_TEMPLATES = {INTERNAL_PACKAGE_INFO_TEMPLATE_NAME, INTERNAL_MODULE_INFO_TEMPLATE_NAME};

  private JavaTemplateUtil() { }

  public static void setClassAndMethodNameProperties (@NotNull Properties properties, @NotNull PsiClass aClass, @NotNull PsiMethod method) {
    properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, notNull(aClass.getQualifiedName(), ""));
    properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, notNull(aClass.getName(), ""));
    properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, method.getName());
  }

  public static @NotNull String getPackageName(@NotNull PsiDirectory directory) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    return aPackage != null ? aPackage.getQualifiedName() : "";
  }

  public static void setPackageNameAttribute(@NotNull Properties properties, @NotNull PsiDirectory directory) {
    properties.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, getPackageName(directory));
  }
}