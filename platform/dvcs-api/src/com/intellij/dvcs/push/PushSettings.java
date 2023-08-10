// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(name = "Push.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class PushSettings implements PersistentStateComponent<PushSettings.State> {
  private State state = new State();

  public static final class State {
    @XCollection(propertyElementName = "force-push-targets")
    public final List<ForcePushTargetInfo> FORCE_PUSH_TARGETS = new ArrayList<>();

    public boolean SHOW_DETAILS_PANEL = true;
  }

  @Override
  public @NotNull State getState() {
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
  }

  public boolean containsForcePushTarget(final @NotNull String remote, final @NotNull String branch) {
    return ContainerUtil.exists(state.FORCE_PUSH_TARGETS,
                                info -> info.targetRemoteName.equals(remote) && info.targetBranchName.equals(branch));
  }

  public void addForcePushTarget(@NotNull String targetRemote, @NotNull String targetBranch) {
    List<ForcePushTargetInfo> targets = state.FORCE_PUSH_TARGETS;
    if (!containsForcePushTarget(targetRemote, targetBranch)) {
      targets.add(new ForcePushTargetInfo(targetRemote, targetBranch));
    }
  }

  public boolean getShowDetailsInPushDialog() {
    return state.SHOW_DETAILS_PANEL;
  }

  public void setShowDetailsInPushDialog(boolean value) {
    state.SHOW_DETAILS_PANEL = value;
  }

  @Tag("force-push-target")
  private static final class ForcePushTargetInfo {
    @Attribute("remote-path") public String targetRemoteName;
    @Attribute("branch") public String targetBranchName;

    @SuppressWarnings("unused")
    ForcePushTargetInfo() {
      this("", "");
    }

    ForcePushTargetInfo(@NotNull String targetRemote, @NotNull String targetBranch) {
      targetRemoteName = targetRemote;
      targetBranchName = targetBranch;
    }
  }
}

