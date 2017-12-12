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
package com.intellij.execution.configurations;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Roman.Chernyatchik
 *
 * Group of linked params. Sometime it's much more convenient to split cmdline in logical groups.
 * In this case it will be easier to patch such grouped argements using custom extentions
 *
 * E.g.: we want to add Coverage support to all existing Ruby run configurations(ruby applications,
 * different kinds of ruby tests, rails configuration, etc). Coverage support require to reorder args
 * in cmdline, add rcov runner script, etc. Without groups it would be harder to parse abstract list of arguments
 */
public final class ParamsGroup implements Cloneable {

  private final String myGroupId;
  private final ParametersList myParamList;

  public ParamsGroup(@NotNull String groupId) {
    this(groupId, new ParametersList());
  }

  private ParamsGroup(@NotNull String groupId, @NotNull ParametersList paramList) {
    myGroupId = groupId;
    myParamList = paramList;
  }

  @NotNull
  public String getId() {
    return myGroupId;
  }

  public void addParameter(@NotNull String parameter) {
    myParamList.add(parameter);
  }

  public void addParameterAt(int index, @NotNull String parameter) {
    myParamList.addAt(index, parameter);
  }

  public void addParameters(@NotNull String... parameters) {
    for (String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameters(@NotNull List<String> parameters) {
    for (String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParametersString(@NotNull String parametersString) {
    addParameters(ParametersList.parse(parametersString));
  }

  public List<String> getParameters() {
    return myParamList.getList();
  }

  public ParametersList getParametersList() {
    return myParamList;
  }

  /** @noinspection MethodDoesntCallSuperMethod*/
  @Override
  public ParamsGroup clone() {
    return new ParamsGroup(myGroupId, myParamList.clone());
   }

  @Override
  public String toString() {
    return myGroupId + ":" + myParamList;
  }
}
