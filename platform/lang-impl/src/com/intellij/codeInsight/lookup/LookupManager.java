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

package com.intellij.codeInsight.lookup;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;

public abstract class LookupManager {
  public static LookupManager getInstance(Project project){
    return ServiceManager.getService(project, LookupManager.class);
  }

  @Nullable
  public static LookupEx getActiveLookup(@Nullable Editor editor) {
    if (editor == null) return null;

    final Project project = editor.getProject();
    if (project == null || project.isDisposed()) return null;

    final LookupEx lookup = getInstance(project).getActiveLookup();
    if (lookup == null) return null;

    return InjectedLanguageUtil.getTopLevelEditor(lookup.getEditor()) == InjectedLanguageUtil.getTopLevelEditor(editor) ? lookup : null;
  }

  public LookupEx showLookup(Editor editor, @NotNull LookupElement... items) {
    return showLookup(editor, items, "", LookupArranger.DEFAULT);
  }

  public LookupEx showLookup(Editor editor, @NotNull LookupElement[] items, String prefix) {
    return showLookup(editor, items, prefix, LookupArranger.DEFAULT);
  }

  public abstract LookupEx showLookup(Editor editor, @NotNull LookupElement[] items, String prefix, @NotNull LookupArranger arranger);

  public abstract void hideActiveLookup();

  @Nullable
  public abstract LookupEx getActiveLookup();

  @NonNls public static final String PROP_ACTIVE_LOOKUP = "activeLookup";

  public abstract void addPropertyChangeListener(PropertyChangeListener listener);
  public abstract void removePropertyChangeListener(PropertyChangeListener listener);

  //public abstract boolean isDisposed();

  public abstract Lookup createLookup(Editor editor, @NotNull LookupElement[] items, final String prefix, LookupArranger arranger);

}