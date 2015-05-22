/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.lineMarker;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class RunLineMarkerContributor {

  private final static ExtensionPointName<RunLineMarkerContributor> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.runLineMarkerContributor");

  public static List<AnAction> getActions(final PsiElement element) {
    return ContainerUtil.mapNotNull(EXTENSION_POINT_NAME.getExtensions(), new NullableFunction<RunLineMarkerContributor, AnAction>() {
      @Nullable
      @Override
      public AnAction fun(RunLineMarkerContributor contributor) {
        return contributor.getAction(element);
      }
    });
  }

  @Nullable
  public abstract AnAction getAction(PsiElement element);
}
