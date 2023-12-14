// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.util.BitUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class ActionClassMetaData {

  private static final int INIT = 0x1;
  private static final int UPDATE = 0x2;
  private static final int CHILDREN = 0x4;
  private static final int POSTPROCESS = 0x8;
  private static final int WRAPPER_UPDATE = 0x10;
  private static final int WRAPPER_CHILDREN = 0x20;
  private static final int WRAPPER_POSTPROCESS = 0x40;

  private ActionClassMetaData() {
  }

  public static boolean isDefaultUpdate(@NotNull AnAction action) {
    return BitUtil.isSet(getFlags(action), UPDATE);
  }

  public static boolean isDefaultGetChildren(@NotNull ActionGroup group) {
    return BitUtil.isSet(getFlags(group), CHILDREN);
  }

  public static boolean isDefaultPostProcessVisibleChildren(@NotNull ActionGroup group) {
    return BitUtil.isSet(getFlags(group), POSTPROCESS);
  }

  public static boolean isWrapperUpdate(@NotNull AnAction action) {
    return BitUtil.isSet(getFlags(action), WRAPPER_UPDATE);
  }

  public static boolean isWrapperGetChildren(@NotNull ActionGroup group) {
    return BitUtil.isSet(getFlags(group), WRAPPER_CHILDREN);
  }

  public static boolean isWrapperPostProcessVisibleChildren(@NotNull ActionGroup group) {
    return BitUtil.isSet(getFlags(group), WRAPPER_POSTPROCESS);
  }

  private static int getFlags(@NotNull AnAction action) {
    if (action.myMetaFlags != 0) return action.myMetaFlags;
    int flags = INIT;
    boolean isGroup = action instanceof ActionGroup;
    {
      Class<?> c = ReflectionUtil.getMethodDeclaringClass(action.getClass(), "update", AnActionEvent.class);
      if (AnAction.class.equals(c)) flags = BitUtil.set(flags, UPDATE, true);
      else if (isActionWrapper(c)) flags = BitUtil.set(flags, WRAPPER_UPDATE, true);
    }

    if (isGroup) {
      Class<?> c = ReflectionUtil.getMethodDeclaringClass(action.getClass(), "getChildren", AnActionEvent.class);
      if (isDefaultActionGroup(c)) flags = BitUtil.set(flags, CHILDREN, true);
      else if (isActionWrapper(c)) flags = BitUtil.set(flags, WRAPPER_CHILDREN, true);
    }

    if (isGroup) {
      Class<?> c = ReflectionUtil.getMethodDeclaringClass(action.getClass(), "postProcessVisibleChildren",
                                                          List.class, UpdateSession.class);
      if (ActionGroup.class.equals(c)) flags = BitUtil.set(flags, POSTPROCESS, true);
      else if (isActionWrapper(c)) flags = BitUtil.set(flags, WRAPPER_POSTPROCESS, true);
    }

    action.myMetaFlags = flags;
    return flags;
  }

  private static boolean isActionWrapper(@Nullable Class<?> c) {
    String s = c == null ? null : c.getName();
    return "com.intellij.openapi.actionSystem.AnActionWrapper".equals(s) ||
           "com.intellij.openapi.actionSystem.ActionGroupWrapper".equals(s);
  }

  private static boolean isDefaultActionGroup(@Nullable Class<?> c) {
    String s = c == null ? null : c.getName();
    return "com.intellij.openapi.actionSystem.DefaultActionGroup".equals(s);
  }
}
