/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.psi.PsiAdapter;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * This is a filtering pattern, used to filter unwanted fields for this action.
 */
public class FilterPattern {

    private static final Logger LOG = Logger.getInstance("#FilterPattern");
    private static final Set<String> loggerNames = new THashSet<>();
    static {
      Collections.addAll(loggerNames,
                         "org.apache.log4j.Logger", "java.util.logging.Logger", "org.apache.commons.logging.Log", "org.slf4j.Logger");
    }

    private String fieldName;
    private String fieldType;
    private String methodName;
    private String methodType;
    private boolean constantField;
    private boolean staticModifier;
    private boolean transientModifier;
    private boolean enumField;
    private boolean loggers;
    private Pattern methodNamePattern;
    private Pattern methodTypePattern;
    private Pattern fieldNamePattern;
    private Pattern fieldTypePattern;

  public boolean fieldMatches(PsiField field) {
    if (isConstantField() && PsiAdapter.isConstantField(field)) {
      return true;
    }
    if (isEnumField() && PsiAdapter.isEnumField(field)) {
      return true;
    }
    if (isStaticModifier() && field.hasModifierProperty(PsiModifier.STATIC)) {
      return true;
    }
    if (isTransientModifier() && field.hasModifierProperty(PsiModifier.TRANSIENT)) {
      return true;
    }
    final Pattern fieldNamePattern = getFieldNamePattern();
    if ((fieldNamePattern != null) && fieldNamePattern.matcher(field.getName()).matches()) {
      return true;
    }
    final String typeText = field.getType().getCanonicalText();
    final Pattern fieldTypePattern = getFieldTypePattern();
    if ((fieldTypePattern != null) && fieldTypePattern.matcher(typeText).matches()) {
      return true;
    }
    if (isLoggers() && loggerNames.contains(typeText)) {
      return true;
    }
    return false;
  }

  public boolean methodMatches(@NotNull PsiMethod method) {
    final String methodName = method.getName();
    final Pattern methodNamePattern = getMethodNamePattern();
    if ((methodNamePattern != null) && methodNamePattern.matcher(methodName).matches()) {
      return true;
    }
    final PsiType returnType = method.getReturnType();
    if (returnType == null) {
      return false;
    }
    final Pattern patternTypePattern = getMethodTypePattern();
    final String methodType = returnType.getCanonicalText();
    return (patternTypePattern != null) && methodTypePattern.matcher(methodType).matches();
  }

    public Pattern getFieldNamePattern() {
      if (StringUtil.isEmpty(fieldName)) {
        return null;
      }
      if (fieldNamePattern == null) {
        try {
          fieldNamePattern = Pattern.compile(fieldName);
        } catch (PatternSyntaxException e) {
          fieldName = null;
          LOG.warn(e.getMessage());
        }
      }
      return fieldNamePattern;
    }

    /**
     * Sets a filtering using regular expression on the field name.
     *
     * @param regexp the regular expression.
     */
    public void setFieldName(String regexp) {
        this.fieldName = regexp;
    }

    public boolean isConstantField() {
        return constantField;
    }

    /**
     * Set this to true to filter by constant fields.
     *
     * @param constantField if true constant fields is unwanted.
     */
    public void setConstantField(boolean constantField) {
        this.constantField = constantField;
    }

    public boolean isTransientModifier() {
        return transientModifier;
    }

    /**
     * Set this to true to filter by transient modifier.
     *
     * @param transientModifier if true fields with transient modifier is unwanted.
     */
    public void setTransientModifier(boolean transientModifier) {
        this.transientModifier = transientModifier;
    }

    public boolean isStaticModifier() {
        return staticModifier;
    }

    /**
     * Set this to true to filter by static modifier.
     *
     * @param staticModifier if true fields with static modifier is unwanted.
     */
    public void setStaticModifier(boolean staticModifier) {
        this.staticModifier = staticModifier;
    }

    public Pattern getMethodNamePattern() {
      if (StringUtil.isEmpty(methodName)) {
        return null;
      }
      if (methodNamePattern == null) {
        try {
          methodNamePattern = Pattern.compile(methodName);
        } catch (PatternSyntaxException e) {
          methodName = null;
          LOG.warn(e.getMessage());
        }
      }
      return methodNamePattern;
    }

    /**
     * Sets a filtering using regular expression on the method name.
     *
     * @param regexp the regular expression.
     */
    public void setMethodName(String regexp) {
        this.methodName = regexp;
    }

    public boolean isEnumField() {
        return enumField;
    }

    /**
     * Set this to true to filter by enum fields (JDK1.5).
     *
     * @param enumField if true enum fields is unwanted.
     * @since 3.17
     */
    public void setEnumField(boolean enumField) {
        this.enumField = enumField;
    }

    public boolean isLoggers() {
        return loggers;
    }

    /**
     * Set this to true to filter loggers (Log4j, JDK1.4).
     *
     * @param loggers if true logger fields is unwanted.
     * @since 3.20
     */
    public void setLoggers(boolean loggers) {
        this.loggers = loggers;
    }

    public Pattern getFieldTypePattern() {
      if (StringUtil.isEmpty(fieldType)) {
        return null;
      }
      if (fieldTypePattern ==  null) {
        try {
          fieldTypePattern = Pattern.compile(fieldType);
        } catch (PatternSyntaxException e) {
          fieldType = null;
          LOG.warn(e.getMessage());
        }
      }
      return fieldTypePattern;
    }

    /**
     * Sets a filtering using the field type FQN.
     *
     * @param fieldType  the field type
     * @since 3.20
     */
    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Pattern getMethodTypePattern() {
      if (StringUtil.isEmpty(methodType)) {
        return null;
      }
      if (methodTypePattern == null) {
        try {
          methodTypePattern = Pattern.compile(methodType);
        } catch (PatternSyntaxException e) {
          methodType = null;
          LOG.warn(e.getMessage());
        }
      }
      return methodTypePattern;
    }

    /**
     * Sets a filtering using the method return type FQN.
     *
     * @param methodType  the method return type
     * @since 3.20
     */
    public void setMethodType(String methodType) {
        this.methodType = methodType;
    }

    public String toString() {
        return "FilterPattern{" +
                "fieldName='" + fieldName + "'" +
                "fieldType='" + fieldType + "'" +
                ", methodName='" + methodName + "'" +
                ", methodType='" + methodType + "'" +
                ", constantField=" + constantField +
                ", staticModifier=" + staticModifier +
                ", transientModifier=" + transientModifier +
                ", enumField=" + enumField +
                ", loggers=" + loggers +
                "}";
    }
}
