// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Group of linked params. Sometime it's much more convenient to split cmdline in logical groups.
 * In this case it will be easier to patch such grouped arguments using custom extensions.
 *
 * E.g.: we want to add Coverage support to all existing Ruby run configurations (Ruby applications,
 * different kinds of tests, Rails configuration, etc). Adding coverage support requires reordering args
 * in command line, adding RCov runner script, etc. Without groups it would be harder to parse abstract list of arguments.
 *
 * @author Roman.Chernyatchik
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