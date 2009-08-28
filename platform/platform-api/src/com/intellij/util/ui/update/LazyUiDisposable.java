package com.intellij.util.ui.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ide.DataManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class LazyUiDisposable<T extends Disposable> implements Activatable {

  private Throwable myAllocation;

  private boolean myWasEverShown;

  private Disposable myParent;
  private T myChild;

  private Project myProject;

  public LazyUiDisposable(@Nullable Disposable parent, @NotNull JComponent ui, @NotNull T child) {
    this(parent, ui, child, null);
  }

  public LazyUiDisposable(@Nullable Disposable parent, @NotNull JComponent ui, @NotNull T child, @Nullable Project project) {
    if (Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("idea.is.internal"))) {
      myAllocation = new Exception();
    }

    myParent = parent;
    myChild = child;
    myProject = project;

    new UiNotifyConnector.Once(ui, this);
  }

  public final void showNotify() {
    if (myWasEverShown) return;

    try {
      final Disposable parent = findParentDisposable();
      initialize(parent, myChild, findProject());
      Disposer.register(parent, myChild);
    } finally {
      myWasEverShown = true;
    }
  }

  public final void hideNotify() {
  }

  protected abstract void initialize(@NotNull Disposable parent, @NotNull T child, @Nullable Project project);

  @NotNull
  private Disposable findParentDisposable() {
    final Disposable parent = findObject(myParent, PlatformDataKeys.UI_DISPOSABLE);
    return parent != null ? parent : Disposer.get("ui");
  }


  @Nullable
  private Project findProject() {
    return (Project) findObject(myProject, PlatformDataKeys.PROJECT);
  }

  private Disposable findObject(Disposable defaultValue, DataKey<? extends Disposable> key) {
    if (defaultValue == null) {
      if (ApplicationManager.getApplication() != null) {
        return key.getData(DataManager.getInstance().getDataContext());
      } else {
        return null;
      }
    } else {
      return defaultValue;
    }
  }

}