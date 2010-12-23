/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Dialog wrapper with validation support
 *
 * @author Konstantin Bulenkov
 * @author Nikolay Matveev
 */
@SuppressWarnings({"SSBasedInspection", "MethodMayBeStatic", "UnusedDeclaration"})
public abstract class DialogWrapperWithValidation extends DialogWrapper {
  private final Alarm myAlarm;
  protected final Project myProject;
  private final int myValidationDelay;
  private String OLD_BORDER = "OLD_BORDER";
  private JComponent myLastErrorComponent = null;
  private boolean myDisposed = false;
  private Boolean myLastPaintButtonBorder = null;

  protected DialogWrapperWithValidation(Project project, boolean canBeParent, int validationDelay) {
    super(project, canBeParent);
    myProject = project;
    myValidationDelay = validationDelay;
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myDisposable);
  }

  protected DialogWrapperWithValidation(Project project, int validationDelay) {
    this(project, true, validationDelay);
  }

  protected DialogWrapperWithValidation(Project project) {
    this(project, true, 300);
  }

  /**
   * Validates a user input and returns <code>null</code> if everything is fine
   * or returns a problem description with component where is the problem has been found.
   *
   * @return <code>null</code> if everything is OK or a problem descriptor
   */
  @Nullable
  protected abstract ValidationResult doValidate();

  /**
   * Controls components highlighting
   *
   * @return <code>true</code> for problem components highlighting. <code>false</code> otherwise.
   */
  protected boolean isShowErrorBorder() {
    return true;
  }

  /**
   * Specifies border color for error highlighting
   *
   * @return color for error highlighting
   */
  protected Color getErrorColor() {
    return Color.RED;
  }

  private void reportProblem(final String message, final JComponent comp) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (isShowErrorBorder()) {
          if (myLastErrorComponent != comp) {
            if (myLastErrorComponent != null) {
              myLastErrorComponent.setBorder((Border)myLastErrorComponent.getClientProperty(OLD_BORDER));
              if (myLastErrorComponent instanceof AbstractButton && myLastPaintButtonBorder != null) {
                ((AbstractButton)myLastErrorComponent).setBorderPainted(myLastPaintButtonBorder);
              }
            }
            myLastErrorComponent = comp;
            if (comp != null) {
              if (comp instanceof AbstractButton) {
                myLastPaintButtonBorder = ((AbstractButton)comp).isBorderPainted();
                ((AbstractButton)comp).setBorderPainted(true);
              }
              final Object border = comp.getClientProperty(OLD_BORDER);
              if (border == null) {
                comp.putClientProperty(OLD_BORDER, comp.getBorder());
              }
              comp.setBorder(BorderFactory.createLineBorder(getErrorColor(), 1));

            }
          }
        }
        setErrorText(message);
        getOKAction().setEnabled(false);
      }

    });
  }

  private void clearProblems() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        setErrorText(null);
        getOKAction().setEnabled(true);
        if (myLastErrorComponent != null) {
          myLastErrorComponent.setBorder((Border)myLastErrorComponent.getClientProperty(OLD_BORDER));
          if (myLastPaintButtonBorder != null && myLastErrorComponent instanceof AbstractButton) {
            ((AbstractButton)myLastErrorComponent).setBorderPainted(myLastPaintButtonBorder);
          }
          myLastErrorComponent = null;
          myLastPaintButtonBorder = null;
        }
      }
    });
  }


  @Override
  protected void init() {
    super.init();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        scheduleValidation();
      }
    });
  }

  @Override
  protected final void doOKAction() {
    if (doValidate() == null) {
      super.doOKAction();
    }
  }

  protected final void scheduleValidation() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        final ValidationResult result = doValidate();
        if (result == null) {
          clearProblems();
        } else {
          reportProblem(result.message, result.component);
        }

        if (!myDisposed) {
          scheduleValidation();
        }
      }
    }, myValidationDelay, ModalityState.current());
  }

  @Nullable
  public final Project getProject() {
    return myProject;
  }

  @Override
  protected void dispose() {
    super.dispose();
    myDisposed = true;
  }

  public static final class ValidationResult {
    public final String message;
    public final JComponent component;

    public ValidationResult(String message, JComponent component) {
      this.message = message;
      this.component = component;
    }
  }
}
