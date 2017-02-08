/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.diff.tools.util.text;

import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil.HighlightPolicySettingAction;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil.IgnorePolicySettingAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.util.containers.ContainerUtil.list;

public class TextDiffProviderBase implements TextDiffProvider {
  private final IgnorePolicySettingAction myIgnorePolicySettingAction;
  private final HighlightPolicySettingAction myHighlightPolicySettingAction;

  public TextDiffProviderBase(@NotNull TextDiffSettings settings,
                              @NotNull Runnable rediff,
                              @NotNull Disposable disposable,
                              @NotNull IgnorePolicy[] ignorePolicies,
                              @NotNull HighlightPolicy[] highlightPolicies) {
    myIgnorePolicySettingAction = new MyIgnorePolicySettingAction(settings, ignorePolicies);
    myHighlightPolicySettingAction = new MyHighlightPolicySettingAction(settings, highlightPolicies);
    settings.addListener(new MyListener(rediff), disposable);
  }

  @NotNull
  @Override
  public List<AnAction> getToolbarActions() {
    return list(myIgnorePolicySettingAction, myHighlightPolicySettingAction);
  }

  @NotNull
  @Override
  public List<AnAction> getPopupActions() {
    return list(Separator.getInstance(),
                myIgnorePolicySettingAction.getActions(),
                Separator.getInstance(),
                myHighlightPolicySettingAction.getActions(),
                Separator.getInstance());
  }

  @NotNull
  public IgnorePolicy getIgnorePolicy() {
    return myIgnorePolicySettingAction.getValue();
  }

  @NotNull
  public HighlightPolicy getHighlightPolicy() {
    return myHighlightPolicySettingAction.getValue();
  }

  public boolean isHighlightingDisabled() {
    return myHighlightPolicySettingAction.getValue() == HighlightPolicy.DO_NOT_HIGHLIGHT;
  }


  @Nullable
  protected String getText(@NotNull IgnorePolicy option) {
    return null;
  }

  @Nullable
  protected String getText(@NotNull HighlightPolicy option) {
    return null;
  }


  private class MyIgnorePolicySettingAction extends IgnorePolicySettingAction {
    public MyIgnorePolicySettingAction(@NotNull TextDiffSettings settings,
                                       @NotNull IgnorePolicy[] ignorePolicies) {
      super(settings, ignorePolicies);
    }

    @NotNull
    @Override
    protected String getText(@NotNull IgnorePolicy option) {
      return notNullize(TextDiffProviderBase.this.getText(option), super.getText(option));
    }
  }

  private class MyHighlightPolicySettingAction extends HighlightPolicySettingAction {
    public MyHighlightPolicySettingAction(@NotNull TextDiffSettings settings,
                                          @NotNull HighlightPolicy[] highlightPolicies) {
      super(settings, highlightPolicies);
    }

    @NotNull
    @Override
    protected String getText(@NotNull HighlightPolicy option) {
      return notNullize(TextDiffProviderBase.this.getText(option), super.getText(option));
    }
  }

  private static class MyListener implements TextDiffSettings.Listener {
    @NotNull private final Runnable myRediff;

    public MyListener(@NotNull Runnable rediff) {
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
