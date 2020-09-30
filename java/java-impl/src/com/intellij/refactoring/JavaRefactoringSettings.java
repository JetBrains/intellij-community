// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@State(name = "RefactoringSettings", storages = @Storage("baseRefactoring.xml"))
public class JavaRefactoringSettings implements PersistentStateComponent<JavaRefactoringSettings> {
  // properties should be public in order to get saved by DefaultExternalizable implementation

  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = true;

  public boolean RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = true;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_CLASS = true;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_METHOD = false;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_FIELD = false;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = true;

  public boolean ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE = true;

  public boolean EXTRACT_INTERFACE_PREVIEW_USAGES = true;

  /**
   * @deprecated no read usages
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public boolean MOVE_PREVIEW_USAGES = true;

  public boolean MOVE_SEARCH_IN_COMMENTS = true;
  public boolean MOVE_SEARCH_FOR_TEXT = true;

  public boolean TYPE_COOK_DROP_CASTS = true;
  public boolean TYPE_COOK_PRESERVE_RAW_ARRAYS = true;
  public boolean TYPE_COOK_LEAVE_OBJECT_PARAMETERIZED_TYPES_RAW = true;
  public boolean TYPE_COOK_EXHAUSTIVE;
  public boolean TYPE_COOK_COOK_OBJECTS;
  public boolean TYPE_COOK_PRODUCE_WILDCARDS;

  public int INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS;
  public int EXTRACT_INTERFACE_JAVADOC;
  public int EXTRACT_SUPERCLASS_JAVADOC;

  public boolean INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE;
  public boolean INTRODUCE_PARAMETER_USE_INITIALIZER;

  public String INTRODUCE_FIELD_VISIBILITY;
  public int PULL_UP_MEMBERS_JAVADOC;
  /**
   * @deprecated no read usages of preview option
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public boolean PUSH_DOWN_PREVIEW_USAGES;

  public boolean INLINE_METHOD_THIS;
  public boolean INLINE_SUPER_CLASS_THIS;
  public boolean INLINE_FIELD_THIS;
  public boolean INLINE_LOCAL_THIS;
  public boolean INHERITANCE_TO_DELEGATION_DELEGATE_OTHER;

  public String INTRODUCE_CONSTANT_VISIBILITY;
  public boolean INTRODUCE_CONSTANT_MOVE_TO_ANOTHER_CLASS;

  public Boolean INTRODUCE_LOCAL_CREATE_FINALS;
  public Boolean INTRODUCE_LOCAL_CREATE_VAR_TYPE = false;
  public Boolean INTRODUCE_PARAMETER_CREATE_FINALS;

  public boolean INLINE_CLASS_SEARCH_IN_COMMENTS = true;
  public boolean INLINE_CLASS_SEARCH_IN_NON_JAVA = true;

  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_INHERITORS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_PARAMETER_IN_HIERARCHY = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_VARIABLES = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_ACCESSORS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_TESTS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_OVERLOADS = true;

  public static JavaRefactoringSettings getInstance() {
    return ServiceManager.getService(JavaRefactoringSettings.class);
  }

  public boolean isToRenameInheritors() {
    return RENAME_INHERITORS;
  }

  public boolean isToRenameVariables() {
    return RENAME_VARIABLES;
  }

  public boolean isToRenameAccessors() {
    return RENAME_ACCESSORS;
  }

  public void setRenameAccessors(boolean renameAccessors) {
    RENAME_ACCESSORS = renameAccessors;
  }

  public void setRenameInheritors(final boolean RENAME_INHERITORS) {
    this.RENAME_INHERITORS = RENAME_INHERITORS;
  }

  public void setRenameVariables(final boolean RENAME_VARIABLES) {
    this.RENAME_VARIABLES = RENAME_VARIABLES;
  }

  public boolean isRenameParameterInHierarchy() {
    return RENAME_PARAMETER_IN_HIERARCHY;
  }

  public void setRenameParameterInHierarchy(boolean rename) {
    this.RENAME_PARAMETER_IN_HIERARCHY = rename;
  }

  @Override
  public JavaRefactoringSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaRefactoringSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean isToRenameTests() {
    return RENAME_TESTS;
  }

  public void setRenameTests(boolean renameTests) {
    this.RENAME_TESTS = renameTests;
  }

  public void setRenameOverloads(boolean renameOverloads) {
    RENAME_OVERLOADS = renameOverloads;
  }

  public boolean isRenameOverloads() {
    return RENAME_OVERLOADS;
  }
}