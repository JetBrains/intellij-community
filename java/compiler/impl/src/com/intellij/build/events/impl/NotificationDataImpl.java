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
package com.intellij.build.events.impl;

import com.intellij.build.events.NotificationData;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class NotificationDataImpl implements NotificationData {
  @NotNull private final Notification notification;
  @NotNull private final NotificationListener myListener;
  @Nullable private final Navigatable navigatable;

  public NotificationDataImpl(@NotNull Notification notification,
                              @NotNull NotificationListener listener,
                              @Nullable Navigatable navigatable) {
    this.notification = notification;
    myListener = listener;
    this.navigatable = navigatable;
  }

  @NotNull
  @Override
  public Notification getNotification() {
    return notification;
  }

  @NotNull
  @Override
  public NotificationListener getListener() {
    return myListener;
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    return navigatable;
  }
}
