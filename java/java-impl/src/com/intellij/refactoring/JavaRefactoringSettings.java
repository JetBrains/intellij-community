/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(name = "RefactoringSettings", storages = @Storage("other.xml"))
public class JavaRefactoringSettings implements PersistentStateComponent<JavaRefactoringSettings> {
  // properties should be public in order to get saved by DefaultExternalizable implementation

  //public boolean RENAME_PREVIEW_USAGES = true;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = true;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = true;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = true;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = true;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = true;

  public boolean RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = true;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_CLASS = true;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_METHOD = true;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_FIELD = true;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = true;

  //public boolean ENCAPSULATE_FIELDS_PREVIEW_USAGES = true;
  public boolean ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE = true;

  public boolean EXTRACT_INTERFACE_PREVIEW_USAGES = true;

  public boolean MOVE_PREVIEW_USAGES = true;
  public boolean MOVE_SEARCH_IN_COMMENTS = true;
  public boolean MOVE_SEARCH_FOR_TEXT = true;


  //public boolean INLINE_METHOD_PREVIEW_USAGES = true;
  //public boolean INLINE_FIELD_PREVIEW_USAGES = true;

  //public boolean CHANGE_SIGNATURE_PREVIEW_USAGES = true;
  public boolean CHANGE_CLASS_SIGNATURE_PREVIEW_USAGES = true;

  public boolean MOVE_INNER_PREVIEW_USAGES = true;

  //public boolean TYPE_COOK_PREVIEW_USAGES = true;
  public boolean TYPE_COOK_DROP_CASTS = true;
  public boolean TYPE_COOK_PRESERVE_RAW_ARRAYS = true;
  public boolean TYPE_COOK_LEAVE_OBJECT_PARAMETERIZED_TYPES_RAW = true;
  public boolean TYPE_COOK_EXHAUSTIVE = false;
  public boolean TYPE_COOK_COOK_OBJECTS = false;
  public boolean TYPE_COOK_PRODUCE_WILDCARDS = false;

  public boolean TYPE_MIGRATION_PREVIEW_USAGES = true;

  //public boolean MAKE_METHOD_STATIC_PREVIEW_USAGES;
  //public boolean INTRODUCE_PARAMETER_PREVIEW_USAGES;
  public int INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS;
  public int EXTRACT_INTERFACE_JAVADOC;
  public int EXTRACT_SUPERCLASS_JAVADOC;
  public boolean TURN_REFS_TO_SUPER_PREVIEW_USAGES;
  public boolean INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE;
  public boolean INTRODUCE_PARAMETER_USE_INITIALIZER;
  public String INTRODUCE_FIELD_VISIBILITY;
  public int PULL_UP_MEMBERS_JAVADOC;
  public boolean PUSH_DOWN_PREVIEW_USAGES;
  public boolean INLINE_METHOD_THIS;
  public boolean INLINE_SUPER_CLASS_THIS;
  public boolean INLINE_FIELD_THIS;
  public boolean INLINE_LOCAL_THIS;
  //public boolean INHERITANCE_TO_DELEGATION_PREVIEW_USAGES;
  public boolean INHERITANCE_TO_DELEGATION_DELEGATE_OTHER;
  //public boolean REPLACE_CONSTRUCTOR_WITH_FACTORY_PREVIEW_USAGES;
  public String INTRODUCE_CONSTANT_VISIBILITY;
  public boolean INTRODUCE_CONSTANT_MOVE_TO_ANOTHER_CLASS = false;
  public boolean CONVERT_TO_INSTANCE_METHOD_PREVIEW_USAGES = true;

  public Boolean INTRODUCE_LOCAL_CREATE_FINALS = null;
  public Boolean INTRODUCE_PARAMETER_CREATE_FINALS = null;

  public boolean INLINE_CLASS_SEARCH_IN_COMMENTS = true;
  public boolean INLINE_CLASS_SEARCH_IN_NON_JAVA = true;

  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_INHERITORS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_PARAMETER_IN_HIERARCHY = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_VARIABLES = true;
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

  public JavaRefactoringSettings getState() {
    return this;
  }

  public void loadState(JavaRefactoringSettings state) {
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