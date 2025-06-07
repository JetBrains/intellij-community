// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil.HighlightPolicySettingAction;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil.IgnorePolicySettingAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public class TextDiffProviderBase implements TextDiffProvider {
  private final IgnorePolicySettingAction myIgnorePolicySettingAction;
  private final HighlightPolicySettingAction myHighlightPolicySettingAction;

  public TextDiffProviderBase(@NotNull TextDiffSettings settings,
                              @NotNull Runnable rediff,
                              @NotNull Disposable disposable,
                              IgnorePolicy @NotNull [] ignorePolicies,
                              HighlightPolicy @NotNull [] highlightPolicies) {
    myIgnorePolicySettingAction = new MyIgnorePolicySettingAction(settings, ignorePolicies);
    myHighlightPolicySettingAction = new MyHighlightPolicySettingAction(settings, highlightPolicies);
    settings.addListener(new MyListener(rediff), disposable);
  }

  @Override
  public @NotNull List<AnAction> getToolbarActions() {
    return Arrays.asList(myIgnorePolicySettingAction, myHighlightPolicySettingAction);
  }

  @Override
  public @NotNull List<AnAction> getPopupActions() {
    return Arrays.asList(Separator.getInstance(), myIgnorePolicySettingAction.getActions(), Separator.getInstance(),
                         myHighlightPolicySettingAction.getActions(), Separator.getInstance());
  }

  public @NotNull IgnorePolicy getIgnorePolicy() {
    return myIgnorePolicySettingAction.getValue();
  }

  public @NotNull HighlightPolicy getHighlightPolicy() {
    return myHighlightPolicySettingAction.getValue();
  }

  public boolean isHighlightingDisabled() {
    return myHighlightPolicySettingAction.getValue() == HighlightPolicy.DO_NOT_HIGHLIGHT;
  }


  protected @Nls @Nullable String getText(@NotNull IgnorePolicy option) {
    return null;
  }

  protected @Nls @Nullable String getText(@NotNull HighlightPolicy option) {
    return null;
  }


  private class MyIgnorePolicySettingAction extends IgnorePolicySettingAction {
    MyIgnorePolicySettingAction(@NotNull TextDiffSettings settings,
                                       IgnorePolicy @NotNull [] ignorePolicies) {
      super(settings, ignorePolicies);
    }

    @Override
    protected @NotNull String getText(@NotNull IgnorePolicy option) {
      String text = TextDiffProviderBase.this.getText(option);
      if (text != null) return text;
      return super.getText(option);
    }
  }

  private class MyHighlightPolicySettingAction extends HighlightPolicySettingAction {
    MyHighlightPolicySettingAction(@NotNull TextDiffSettings settings,
                                          HighlightPolicy @NotNull [] highlightPolicies) {
      super(settings, highlightPolicies);
    }

    @Override
    protected @NotNull String getText(@NotNull HighlightPolicy option) {
      String text = TextDiffProviderBase.this.getText(option);
      if (text != null) return text;
      return super.getText(option);
    }
  }

  private static class MyListener extends TextDiffSettings.Listener.Adapter {
    private final @NotNull Runnable myRediff;

    MyListener(@NotNull Runnable rediff) {
      myRediff = rediff;
    }

    @Override
    public void highlightPolicyChanged() {
      myRediff.run();
    }

    @Override
    public void ignorePolicyChanged() {
      myRediff.run();
    }
  }
}
