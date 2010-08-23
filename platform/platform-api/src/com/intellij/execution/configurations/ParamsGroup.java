/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
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
public class ParamsGroup implements Cloneable{
  private static final Logger LOG = Logger.getInstance(ParamsGroup.class.getName());

  private String myGroupId;
  private ParametersList myGroupParams = new ParametersList();

  public ParamsGroup(@NotNull final String groupId) {
    myGroupId = groupId;
  }

  public String getId() {
    return myGroupId;
  }

  public void addParameter(@NotNull @NonNls final String parameter) {
    myGroupParams.add(parameter);
  }

  public void addParameters(final String... parameters) {
    for (String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameters(final List<String> parameters) {
    for (final String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParametersString(final String parameters) {
    if (parameters != null) {
      final String[] parms = ParametersList.parse(parameters);
      for (String parm : parms) {
        addParameter(parm);
      }
    }
  }

  public List<String> getParameters() {
    return myGroupParams.getList();
  }

  public ParametersList getParametersList() {
    return myGroupParams;
  }

  public ParamsGroup clone() {
     try {
       final ParamsGroup clone = (ParamsGroup)super.clone();
       clone.myGroupId = myGroupId;
       clone.myGroupParams = myGroupParams.clone();
       return clone;
     }
     catch (CloneNotSupportedException e) {
       LOG.error(e);
       return null;
     }
   }

  @Override
  public String toString() {
    return myGroupId;
  }
}
