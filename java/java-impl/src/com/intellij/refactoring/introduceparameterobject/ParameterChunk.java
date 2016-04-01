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
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ParameterChunk {
  private final VariableData parameter;
  private PsiField field;
  private String getter;
  private String setter;

  public ParameterChunk(VariableData parameter) {
    this.parameter = parameter;
  }

  public void setField(PsiField field) {
    this.field = field;
  }

  public void setGetter(String getter) {
    this.getter = getter;
  }

  public void setSetter(String setter) {
    this.setter = setter;
  }

  @Nullable
  public PsiField getField() {
    return field;
  }

  @Nullable
  public static ParameterChunk getChunkByParameter(PsiParameter param, List<ParameterChunk> params) {
    for (ParameterChunk chunk : params) {
      if (chunk.getParameter().variable.equals(param)) {
        return chunk;
      }
    }
    return null;
  }

  public VariableData getParameter() {
    return parameter;
  }

  public String getGetter() {
    return getter;
  }

  public String getSetter() {
    return setter;
  }

  @NotNull
  public String getSetterName(Project project) {
    @NonNls String setter = getSetter();
    if (setter == null) {
      setter = getField() != null ? GenerateMembersUtil.suggestSetterName(getField())
                                  : GenerateMembersUtil.suggestSetterName(parameter.name, parameter.type, project);
    }

    return setter;
  }

  @NotNull
  public String getGetterName(Project project) {
    @NonNls String getter = getGetter();
    if (getter == null) {
      getter = getField() != null ? GenerateMembersUtil.suggestGetterName(getField())
                                  : GenerateMembersUtil.suggestGetterName(parameter.name, parameter.type, project);
    }
    return getter;
  }
}
