// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.intellij.util.ReflectionUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
@State(name = "StructureViewFactory", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class StructureViewFactoryImpl extends StructureViewFactoryEx implements PersistentStateComponent<StructureViewFactoryImpl.State> {
  private static final ExtensionPointName<StructureViewExtension> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.lang.structureViewExtension");

  public static final class State {
    @SuppressWarnings("WeakerAccess") public boolean AUTOSCROLL_MODE = true;
    @SuppressWarnings("WeakerAccess") public boolean AUTOSCROLL_FROM_SOURCE = false;
    @SuppressWarnings("WeakerAccess") public String ACTIVE_ACTIONS = "";
  }

  private final Project myProject;
  private final CoroutineScope myCoroutineScope;
  private StructureViewWrapperImpl myStructureViewWrapperImpl;
  private State myState = new State();
  private Runnable myRunWhenInitialized = null;

  private final Map<Class<? extends PsiElement>, Collection<StructureViewExtension>> myImplExtensions = new ConcurrentHashMap<>();

  public StructureViewFactoryImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myProject = project;
    myCoroutineScope = coroutineScope;
    project.getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        myImplExtensions.clear();
        if (myStructureViewWrapperImpl != null) {
          myStructureViewWrapperImpl.rebuildNow("dynamic plugins changed");
        }
      }
    });
  }

  @Override
  public StructureViewWrapper getStructureViewWrapper() {
    return myStructureViewWrapperImpl;
  }

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    myStructureViewWrapperImpl = new StructureViewWrapperImpl(myProject, toolWindow, myCoroutineScope);
    if (myRunWhenInitialized != null) {
      myRunWhenInitialized.run();
      myRunWhenInitialized = null;
    }
  }

  @Override
  public @NotNull Collection<StructureViewExtension> getAllExtensions(@NotNull Class<? extends PsiElement> type) {
    Collection<StructureViewExtension> result = myImplExtensions.get(type);
    if (result != null) {
      return result;
    }

    ExtensionPointImpl<@NotNull StructureViewExtension> point = (ExtensionPointImpl<@NotNull StructureViewExtension>)EXTENSION_POINT_NAME.getPoint();
    Set<Class<? extends PsiElement>> visitedTypes = new HashSet<>();
    result = new ArrayList<>();
    for (StructureViewExtension extension : point.getExtensionList()) {
      Class<? extends PsiElement> registeredType = extension.getType();
      if (ReflectionUtil.isAssignable(registeredType, type) && visitedTypes.add(registeredType)) {
        result.addAll(ExtensionProcessingHelper.INSTANCE.getByGroupingKey(point, StructureViewExtension.class, registeredType, StructureViewExtension::getType));
      }
    }

    Collection<StructureViewExtension> oldValue = myImplExtensions.putIfAbsent(type, result);
    return oldValue == null ? result : oldValue;
  }

  @Override
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

  public @NotNull Collection<String> collectActiveActions() {
    return new LinkedHashSet<>(Arrays.asList(myState.ACTIVE_ACTIONS.split(",")));
  }

  @Override
  public boolean isActionActive(final String name) {
    return collectActiveActions().contains(name);
  }

  @Override
  public void runWhenInitialized(@NotNull Runnable runnable) {
    if (myStructureViewWrapperImpl != null) {
      runnable.run();
    }
    else {
      myRunWhenInitialized = runnable;
    }
  }

  @Override
  public @NotNull StructureView createStructureView(FileEditor fileEditor, @NotNull StructureViewModel treeModel, @NotNull Project project) {
    return createStructureView(fileEditor, treeModel, project, true);
  }

  @Override
  public @NotNull StructureView createStructureView(FileEditor fileEditor,
                                                    @NotNull StructureViewModel treeModel,
                                                    @NotNull Project project,
                                                    boolean showRootNode) {
    return new StructureViewComponent(fileEditor, treeModel, project, showRootNode);
  }

  @TestOnly
  public void cleanupForNextTest() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myState = new State();
  }
}