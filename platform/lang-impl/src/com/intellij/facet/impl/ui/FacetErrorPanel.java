/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FacetErrorPanel {
  private final JPanel myMainPanel;
  private JPanel myButtonPanel;
  private JButton myQuickFixButton;
  private FacetConfigurationQuickFix myCurrentQuickFix;
  private final JLabel myWarningLabel;
  private final FacetValidatorsManagerImpl myValidatorsManager;
  private boolean myNoErrors = true;
  private final List<Runnable> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Disposable myParentDisposable;

  public FacetErrorPanel() {
    this(null);
  }

  public FacetErrorPanel(@Nullable Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
    myValidatorsManager = new FacetValidatorsManagerImpl();
    myWarningLabel = new JLabel();
    myWarningLabel.setIcon(AllIcons.General.WarningDialog);
    myWarningLabel.setIconTextGap(5);
    myQuickFixButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        if (myCurrentQuickFix != null) {
          myCurrentQuickFix.run(myQuickFixButton);
          myValidatorsManager.validate();
        }
      }
    });
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(BorderLayout.EAST, myButtonPanel);
    myMainPanel.add(BorderLayout.CENTER, myWarningLabel);
    setNoErrors();
  }

  public void addListener(Runnable listener) {
    myListeners.add(listener);
  }

  private void changeValidity(final boolean noErrors) {
    myNoErrors = noErrors;
    for (Runnable listener : myListeners) {
      listener.run();
    }
  }

  private void setNoErrors() {
    myMainPanel.setVisible(false);
    myWarningLabel.setVisible(false);
    myQuickFixButton.setVisible(false);
    changeValidity(true);
  }

  public void disposeUIResources() {
    myCurrentQuickFix = null;
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  public boolean isOk() {
    return myNoErrors;
  }

  @NotNull
  public FacetValidatorsManager getValidatorsManager() {
    return myValidatorsManager;
  }

  private class FacetValidatorsManagerImpl implements FacetValidatorsManager {
    private final List<FacetEditorValidator> myValidators = new ArrayList<>();
    private final List<FacetEditorValidator> mySlowValidators = new SmartList<>();
    private final Set<CancellablePromise<ValidationResult>> myChecks = new HashSet<>();

    @Override
    public void registerValidator(final FacetEditorValidator validator, JComponent... componentsToWatch) {
      if (validator instanceof SlowFacetEditorValidator) {
        if (myParentDisposable == null) {
          throw new IllegalArgumentException("SlowFacetEditorValidator could not be registered if parent disposable is null");
        }
        mySlowValidators.add(validator);
      }
      else {
        myValidators.add(validator);
      }
      final UserActivityWatcher watcher = new UserActivityWatcher();
      for (JComponent component : componentsToWatch) {
        watcher.register(component);
      }
      watcher.addUserActivityListener(new UserActivityListener() {
        @Override
        public void stateChanged() {
          validate();
        }
      });
    }

    @Override
    public void validate() {
      cancelChecks();

      for (FacetEditorValidator validator : myValidators) {
        ValidationResult validationResult = validator.check();
        if (!validationResult.isOk()) {
          validationCompleted(validationResult);
          return;
        }
      }

      for (FacetEditorValidator validator : mySlowValidators) {
        Ref<CancellablePromise<ValidationResult>> ref = new Ref<>();
        CancellablePromise<ValidationResult> promise = ReadAction
          .nonBlocking(() -> validator.check())
          .expireWith(myParentDisposable)
          .finishOnUiThread(ModalityState.any(), validationResult -> {
            // Current modality state could not be used,
            // because validate() may be called on facet editor init
            // before dialog's show() changes modality state from non-modal.
            if (myChecks.remove(ref.get())) {
              if (!validationResult.isOk()) {
                validationCompleted(validationResult);
              }
              else if (myChecks.isEmpty()) {
                myCurrentQuickFix = null;
                setNoErrors();
              }
            }
          })
          .submit(NonUrgentExecutor.getInstance());
        ref.set(promise);
        myChecks.add(promise);
      }

      if (myChecks.isEmpty()) {
        myCurrentQuickFix = null;
        setNoErrors();
      }
    }

    void cancelChecks() {
      for (CancellablePromise<ValidationResult> promise : myChecks) {
        if (!promise.isDone()) {
          promise.cancel();
        }
      }
      myChecks.clear();
    }

    private void validationCompleted(ValidationResult validationResult) {
      cancelChecks();
      myMainPanel.setVisible(true);
      myWarningLabel.setText(XmlStringUtil.wrapInHtml(validationResult.getErrorMessage()));
      myWarningLabel.setVisible(true);
      myCurrentQuickFix = validationResult.getQuickFix();
      myQuickFixButton.setVisible(myCurrentQuickFix != null);
      if (myCurrentQuickFix != null) {
        String buttonText = myCurrentQuickFix.getFixButtonText();
        myQuickFixButton.setText(buttonText != null ? buttonText : IdeBundle.message("button.facet.quickfix.text"));
      }
      changeValidity(false);
    }
  }
}
