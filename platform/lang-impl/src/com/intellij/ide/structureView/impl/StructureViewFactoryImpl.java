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

package com.intellij.ide.structureView.impl;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * @author Eugene Belyaev
 */

@State(
  name="StructureViewFactory",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public final class StructureViewFactoryImpl extends StructureViewFactoryEx implements PersistentStateComponent<StructureViewFactoryImpl.State> {
  public static class State {
    @SuppressWarnings({"WeakerAccess"}) public boolean AUTOSCROLL_MODE = true;
    @SuppressWarnings({"WeakerAccess"}) public boolean AUTOSCROLL_FROM_SOURCE = false;
    @SuppressWarnings({"WeakerAccess"}) public String ACTIVE_ACTIONS = "";
  }

  private final Project myProject;
  private StructureViewWrapperImpl myStructureViewWrapperImpl;
  private State myState = new State();
  private Runnable myRunWhenInitialized = null;

  private static final NotNullLazyValue<MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>> myExtensions = new NotNullLazyValue<MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>>() {
    @NotNull
    @Override
    protected MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> compute() {
      MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> map =
        new MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>();
      StructureViewExtension[] extensions = Extensions.getExtensions(StructureViewExtension.EXTENSION_POINT_NAME);
      for (StructureViewExtension extension : extensions) {
        map.put(extension.getType(), extension);
      }
      return map;
    }
  };
  private final MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> myImplExtensions = new MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>();

  public StructureViewFactoryImpl(Project project) {
    myProject = project;
  }

  public StructureViewWrapper getStructureViewWrapper() {
    return myStructureViewWrapperImpl;
  }

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  public void initToolWindow(ToolWindow toolWindow) {
    myStructureViewWrapperImpl = new StructureViewWrapperImpl(myProject);
    final Content content = ContentFactory.SERVICE.getInstance().createContent(myStructureViewWrapperImpl.getComponent(), "", false);
    Disposer.register(content, myStructureViewWrapperImpl);
    toolWindow.getContentManager().addContent(content);

    if (myRunWhenInitialized != null) {
      myRunWhenInitialized.run();
      myRunWhenInitialized = null;
    }
  }

  public Collection<StructureViewExtension> getAllExtensions(Class<? extends PsiElement> type) {
    Collection<StructureViewExtension> result = myImplExtensions.get(type);
    if (result == null) {
      MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> map = myExtensions.getValue();
      for (Class<? extends PsiElement> registeredType : map.keySet()) {
        if (ReflectionCache.isAssignable(registeredType, type)) {
          final Collection<StructureViewExtension> extensions = map.get(registeredType);
          for (StructureViewExtension extension : extensions) {
            myImplExtensions.put(type, extension);
          }
        }
      }
      result = myImplExtensions.get(type);
      if (result == null) return Collections.emptyList();
    }
    return result;
  }

  public void setActiveAction(final String name, final boolean state) {
    Collection<String> activeActions = collectActiveActions();

    if (state) {
      activeActions.add(name);
    }
    else {
      activeActions.remove(name);
    }

    myState.ACTIVE_ACTIONS = toString(activeActions);
  }

  private static String toString(final Collection<String> activeActions) {
    return StringUtil.join(activeActions, ",");
  }

  private Collection<String> collectActiveActions() {
    final String[] strings = myState.ACTIVE_ACTIONS.split(",");
    return new HashSet<String>(Arrays.asList(strings));
  }

  public boolean isActionActive(final String name) {
    return collectActiveActions().contains(name);
  }

  @Override
  public void runWhenInitialized(Runnable runnable) {
    if (myStructureViewWrapperImpl != null) {
      runnable.run();
    }
    else {
      myRunWhenInitialized = runnable;
    }
  }

  public StructureView createStructureView(final FileEditor fileEditor, final StructureViewModel treeModel, final Project project) {
    return new StructureViewComponent(fileEditor, treeModel, project);
  }

  public StructureView createStructureView(final FileEditor fileEditor,
                                           final StructureViewModel treeModel, final Project project, final boolean showRootNode) {
    return new StructureViewComponent(fileEditor, treeModel, project, showRootNode);
  }
}
