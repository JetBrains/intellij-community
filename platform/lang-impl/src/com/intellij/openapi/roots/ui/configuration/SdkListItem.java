// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.NewSdkAction;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

public abstract class SdkListItem {
  private SdkListItem() { }

  /**
   * A class the represents a reference to an {@link Sdk}. Is it up to
   * the code that creates it to interpret a possible selections items
   * of that type.
   */
  public static final class SdkReferenceItem extends SdkListItem {
    public final @NotNull SdkType sdkType;
    public final @NotNull String name;
    public final @NlsSafe @Nullable String versionString;
    public final boolean hasValidPath;

    SdkReferenceItem(@NotNull SdkType sdkType, @NotNull String name, @Nullable String versionString, boolean hasValidPath) {
      this.sdkType = sdkType;
      this.name = name;
      this.versionString = versionString;
      this.hasValidPath = hasValidPath;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SdkReferenceItem item)) return false;
      return sdkType.equals(item.sdkType) && name.equals(item.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sdkType, name);
    }
  }

  public abstract static class SdkItem extends SdkListItem {
    public final @NotNull Sdk sdk;

    SdkItem(@NotNull Sdk sdk) {
      this.sdk = sdk;
    }

    @Override
    public final boolean equals(Object o) {
      return this == o || o instanceof SdkItem && sdk.equals(((SdkItem)o).sdk);
    }

    @Override
    public final int hashCode() {
      return sdk.hashCode();
    }

    abstract boolean hasSameSdk(@NotNull Sdk value);

    @Override
    public String toString() {
      return sdk.getName();
    }
  }

  public static final class ProjectSdkItem extends SdkListItem {
    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ProjectSdkItem;
    }
  }

  public static final class NoneSdkItem extends SdkListItem {
    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof NoneSdkItem;
    }
  }

  public static final class InvalidSdkItem extends SdkListItem {
    public final @NotNull String sdkName;

    @VisibleForTesting
    @ApiStatus.Internal
    public InvalidSdkItem(@NotNull String name) {
      sdkName = name;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof InvalidSdkItem && sdkName.equals(((InvalidSdkItem)o).sdkName);
    }

    @Override
    public int hashCode() {
      return sdkName.hashCode();
    }
  }

  public static final class SuggestedItem extends SdkListItem {
    public final @NotNull SdkType sdkType;
    public final @NlsSafe String version;
    @NlsSafe public final @NotNull String homePath;
    public final @NotNull Boolean isSymlink;

    SuggestedItem(@NotNull SdkType sdkType, @NlsSafe @NotNull String version, @NotNull String homePath) {
      this.sdkType = sdkType;
      this.version = version;
      this.homePath = homePath;
      this.isSymlink = false;
    }

    SuggestedItem(@NotNull SdkType sdkType, @NotNull SdkType.SdkEntry entry) {
      this.sdkType = sdkType;
      this.version = entry.versionString();
      this.homePath = entry.homePath();
      this.isSymlink = Boolean.TRUE.equals(entry.isSymlink());
    }
  }

  public enum ActionRole {DOWNLOAD, ADD}

  public static final class ActionItem extends SdkListItem {
    public final @NotNull ActionRole role;
    public final @NotNull NewSdkAction action;
    public final @Nullable GroupItem group;

    ActionItem(@NotNull ActionRole role, @NotNull NewSdkAction action, @Nullable GroupItem group) {
      this.role = role;
      this.action = action;
      this.group = group;
    }

    @Contract(pure = true)
    @NotNull ActionItem withGroup(@NotNull GroupItem group) {
      return new ActionItem(role, action, group);
    }

    @Override
    public String toString() {
      return action.getListItemText();
    }
  }

  public static final class GroupItem extends SdkListItem {
    public final @NotNull Icon icon;
    public final @Nls @NotNull String caption;
    public final @NotNull List<? extends SdkListItem> subItems;

    GroupItem(@NotNull Icon icon, @Nls @NotNull String caption, @NotNull List<ActionItem> subItems) {
      this.icon = icon;
      this.caption = caption;
      this.subItems = List.copyOf(ContainerUtil.map(subItems, it -> it.withGroup(this)));
    }
  }
}
