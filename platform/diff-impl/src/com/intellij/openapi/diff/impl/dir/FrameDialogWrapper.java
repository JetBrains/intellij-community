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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class FrameDialogWrapper {
  public enum Mode {FRAME, MODAL, NON_MODAL}

  @NotNull
  protected abstract JComponent getPanel();

  @Nullable
  protected String getDimensionServiceKey() {
    return null;
  }

  @Nullable
  protected JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Nullable
  protected String getTitle() {
    return null;
  }

  @Nullable
  protected Project getProject() {
    return null;
  }

  @NotNull
  protected Mode getMode() {
    return Mode.MODAL;
  }

  @Nullable
  protected Disposable getDisposable() {
    return null;
  }

  public void show() {
    switch (getMode()) {
      case FRAME:
        new MyFrameWrapper(getProject(), getMode(), getPanel(), getPreferredFocusedComponent(), getTitle(), getDimensionServiceKey(),
                           getDisposable())
          .show();
        return;
      case MODAL:
      case NON_MODAL:
        new MyDialogWrapper(getProject(), getMode(), getPanel(), getPreferredFocusedComponent(), getTitle(), getDimensionServiceKey(),
                            getDisposable())
          .show();
        return;
      default:
        throw new IllegalArgumentException(getMode().toString());
    }
  }

  private static class MyDialogWrapper extends DialogWrapper {
    private final JComponent myComponent;
    private final JComponent myPreferredFocusedComponent;
    private final String myDimensionServiceKey;

    public MyDialogWrapper(@Nullable Project project,
                           @NotNull Mode mode,
                           @NotNull JComponent component,
                           @Nullable JComponent preferredFocusedComponent,
                           @Nullable String title,
                           @Nullable String dimensionServiceKey,
                           @Nullable Disposable disposable) {
      super(project, true);
      myComponent = component;
      myPreferredFocusedComponent = preferredFocusedComponent;
      myDimensionServiceKey = dimensionServiceKey;

      if (title != null) {
        setTitle(title);
      }
      switch (mode) {
        case MODAL:
          setModal(true);
          break;
        case NON_MODAL:
          setModal(false);
          break;
        default:
          throw new IllegalArgumentException(mode.toString());
      }

      if (disposable != null) {
        Disposer.register(getDisposable(), disposable);
      }

      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      return myComponent;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myPreferredFocusedComponent;
    }

    @Nullable
    @Override
    protected String getDimensionServiceKey() {
      return myDimensionServiceKey;
    }

    // it is information dialog - no need to OK or Cancel. Close the dialog by clicking the cross button or pressing Esc.
    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[0];
    }
  }

  private static class MyFrameWrapper extends FrameWrapper {
    public MyFrameWrapper(@Nullable Project project,
                          @NotNull Mode mode,
                          @NotNull JComponent component,
                          @Nullable JComponent preferredFocusedComponent,
                          @Nullable String title,
                          @Nullable String dimensionServiceKey,
                          @Nullable Disposable disposable) {
      super(project, dimensionServiceKey);

      assert mode == Mode.FRAME;

      if (title != null) {
        setTitle(title);
      }
      setComponent(component);
      setPreferredFocusedComponent(preferredFocusedComponent);
      closeOnEsc();
      if (disposable != null) {
        addDisposable(disposable);
      }
    }
  }
}
