/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "Push.Settings", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class PushSettings implements PersistentStateComponent<PushSettings.State> {

  private State myState = new State();

  public static class State {
    @XCollection(propertyElementName = "force-push-targets")
    public List<ForcePushTargetInfo> FORCE_PUSH_TARGETS = ContainerUtil.newArrayList();
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public boolean containsForcePushTarget(@NotNull final String remote, @NotNull final String branch) {
    return ContainerUtil.exists(myState.FORCE_PUSH_TARGETS,
                                info -> info.targetRemoteName.equals(remote) && info.targetBranchName.equals(branch));
  }

  public void addForcePushTarget(@NotNull String targetRemote, @NotNull String targetBranch) {
    List<ForcePushTargetInfo> targets = myState.FORCE_PUSH_TARGETS;
    if (!containsForcePushTarget(targetRemote, targetBranch)) {
      targets.add(new ForcePushTargetInfo(targetRemote, targetBranch));
      myState.FORCE_PUSH_TARGETS = targets;
    }
  }


  @Tag("force-push-target")
  private static class ForcePushTargetInfo {
    @Attribute(value = "remote-path") public String targetRemoteName;
    @Attribute(value = "branch") public String targetBranchName;

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

