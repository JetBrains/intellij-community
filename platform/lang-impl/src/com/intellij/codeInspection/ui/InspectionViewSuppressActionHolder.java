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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CustomSuppressableInspectionTool;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.Interner;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InspectionViewSuppressActionHolder {
  private final Map<String, Map<Language, SuppressIntentionAction[]>> mySuppressActions = FactoryMap.createMap(__ -> new THashMap<>());
  private final Interner<Set<SuppressIntentionAction>> myActionSetInterner = new Interner<>();

  @NotNull
  public synchronized SuppressIntentionAction[] getSuppressActions(@NotNull InspectionToolWrapper wrapper, @NotNull PsiElement context) {
    return mySuppressActions.get(wrapper.getShortName()).computeIfAbsent(context.getLanguage(), __ -> {
      final InspectionProfileEntry tool = wrapper.getTool();
      SuppressIntentionAction[] actions;
      if (tool instanceof CustomSuppressableInspectionTool) {
        actions = ((CustomSuppressableInspectionTool)tool).getSuppressActions(null);
      } else {
        actions = Stream.of(tool.getBatchSuppressActions(context))
          .map(fix -> SuppressIntentionActionFromFix.convertBatchToSuppressIntentionAction(fix))
          .toArray(SuppressIntentionAction[]::new);
      }
      return actions == null ? SuppressIntentionAction.EMPTY_ARRAY : actions;
    });
  }

  @NotNull
  public synchronized Set<SuppressIntentionAction> getSuppressActions(@NotNull InspectionToolWrapper wrapper) {
    return mySuppressActions.get(wrapper.getShortName()).values().stream().flatMap(Arrays::stream).collect(Collectors.toSet());
  }

  public Set<SuppressIntentionAction> internSuppressActions(@NotNull Set<SuppressIntentionAction> set) {
    synchronized (myActionSetInterner) {
      return myActionSetInterner.intern(set);
    }
  }
}
