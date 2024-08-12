// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class FacetErrorPanel {
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

  public @NotNull FacetValidatorsManager getValidatorsManager() {
    return myValidatorsManager;
  }

  private final class FacetValidatorsManagerImpl implements FacetValidatorsManager {
    private final List<FacetEditorValidator> myValidators = new ArrayList<>();
    private final List<FacetEditorValidator> mySlowValidators = new SmartList<>();
    private final Set<Future<?>> myChecks = new HashSet<>();

    FacetValidatorsManagerImpl() {
      if (myParentDisposable != null) {
        Disposer.register(myParentDisposable, () -> cancelChecks());
      }
    }

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
        myChecks.add(getSlowCheck(validator));
      }

      if (myChecks.isEmpty()) {
        myCurrentQuickFix = null;
        setNoErrors();
      }
    }

    private Future<?> getSlowCheck(FacetEditorValidator validator) {
      assert validator instanceof SlowFacetEditorValidator;

      Ref<Future<?>> ref = new Ref<>();
      Future<?> result;
      CompletableFuture<ValidationResult> check = ((SlowFacetEditorValidator)validator).checkAsync();

      // Current modality state could not be used,
      // because validate() may be called on facet editor init
      // before dialog's show() changes modality state from non-modal.
      if (check != null) {
        result = check.thenAccept(validationResult -> {
          AppUIExecutor.onUiThread(ModalityState.any()).expireWith(myParentDisposable).submit(() -> {
            validationCompleted(ref, validationResult);
          });
        });
      }
      else {
        result = ReadAction
          .nonBlocking(() -> validator.check())
          .expireWith(myParentDisposable)
          .finishOnUiThread(ModalityState.any(), validationResult -> {
            validationCompleted(ref, validationResult);
          })
          .submit(NonUrgentExecutor.getInstance());
      }

      ref.set(result);
      return result;
    }

    void cancelChecks() {
      for (Future<?> check : myChecks) {
        if (!check.isDone()) {
          check.cancel(true);
        }
      }
      myChecks.clear();
    }

    private void validationCompleted(Ref<Future<?>> ref, ValidationResult validationResult) {
      if (myChecks.remove(ref.get())) {
        if (!validationResult.isOk()) {
          validationCompleted(validationResult);
        }
        else if (myChecks.isEmpty()) {
          myCurrentQuickFix = null;
          setNoErrors();
        }
      }
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
