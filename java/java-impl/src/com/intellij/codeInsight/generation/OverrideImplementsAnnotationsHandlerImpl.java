/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class OverrideImplementsAnnotationsHandlerImpl implements OverrideImplementsAnnotationsHandler {
  @Override
  public String[] getAnnotations(Project project) {
    List<String> annotations = new ArrayList<>();

    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    annotations.addAll(manager.getNotNulls());
    annotations.addAll(manager.getNullables());

    annotations.add(AnnotationUtil.NLS);

    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    annotations.addAll(settings.getCustomSettings(JavaCodeStyleSettings.class).getRepeatAnnotations());

    return ArrayUtil.toStringArray(annotations);
  }
}