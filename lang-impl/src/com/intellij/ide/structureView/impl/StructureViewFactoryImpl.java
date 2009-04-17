package com.intellij.ide.structureView.impl;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ReflectionCache;

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

  private final MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> myExtensions = new MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>();
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
    final FileEditor[] fileEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
    if (fileEditors.length > 0) {
      myStructureViewWrapperImpl.setFileEditor(fileEditors [0]);
    }
    if (myRunWhenInitialized != null) {
      myRunWhenInitialized.run();
      myRunWhenInitialized = null;
    }
  }

  public void registerExtension(Class<? extends PsiElement> type, StructureViewExtension extension) {
    myExtensions.put(type, extension);
    myImplExtensions.clear();
  }

  public void unregisterExtension(Class<? extends PsiElement> type, StructureViewExtension extension) {
    myExtensions.remove(type, extension);
    myImplExtensions.clear();
  }

  public Collection<StructureViewExtension> getAllExtensions(Class<? extends PsiElement> type) {
    Collection<StructureViewExtension> result = myImplExtensions.get(type);
    if (result == null) {
      for (Class<? extends PsiElement> registeredType : myExtensions.keySet()) {
        if (ReflectionCache.isAssignable(registeredType, type)) {
          final Collection<StructureViewExtension> extensions = myExtensions.get(registeredType);
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
