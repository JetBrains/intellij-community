// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import com.intellij.notification.Notification;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public interface Failure {
  @Nullable
  @BuildEventsNls.Message
  String getMessage();

  @Nullable
  @BuildEventsNls.Description
  String getDescription();

  default @Nullable Throwable getError() {return null;}

  List<? extends Failure> getCauses();

  default @Nullable Notification getNotification() {return null;}

  default @Nullable Navigatable getNavigatable() {return null;}
}
