/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 19-Aug-2008
 */
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class OverrideImplementsAnnotationsHandlerImpl implements OverrideImplementsAnnotationsHandler {
  @Override
  public String[] getAnnotations(Project project) {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    final Collection<String> anns = new ArrayList<>(manager.getNotNulls());
    anns.addAll(manager.getNullables());
    anns.add(AnnotationUtil.NLS);
    return ArrayUtil.toStringArray(anns);
  }

  @Override
  @NotNull
  public String[] annotationsToRemove(Project project, @NotNull final String fqName) {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (manager.getNotNulls().contains(fqName)) {
      return ArrayUtil.toStringArray(manager.getNullables());
    }
    if (manager.getNullables().contains(fqName)) {
      return ArrayUtil.toStringArray(manager.getNotNulls()); 
    }
    if (Comparing.strEqual(fqName, AnnotationUtil.NLS)){
      return new String[]{AnnotationUtil.NON_NLS};
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }
}
