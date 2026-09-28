// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.Failure;
import com.intellij.notification.Notification;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class FailureImpl implements Failure {

  private final @BuildEventsNls.Message String myMessage;
  private final @BuildEventsNls.Description String myDescription;
  private final List<? extends Failure> myCauses;
  private final @Nullable Throwable myError;
  private final @Nullable Notification myNotification;
  private final @Nullable Navigatable myNavigatable;

  public FailureImpl(@BuildEventsNls.Message String message, Throwable error) {
    this(message, null, Collections.emptyList(), error, null, null);
  }

  public FailureImpl(@BuildEventsNls.Message String message,
                     Throwable error,
                     @Nullable Notification notification,
                     @Nullable Navigatable navigatable) {
    this(message, null, Collections.emptyList(), error, notification, navigatable);
  }

  public FailureImpl(@BuildEventsNls.Message String message, @BuildEventsNls.Description String description) {
    this(message, description, Collections.emptyList(), null, null, null);
  }

  public FailureImpl(@BuildEventsNls.Message String message,
                     @BuildEventsNls.Description String description,
                     @NotNull List<? extends Failure> causes) {
    this(message, description, causes, null, null, null);
  }

  public FailureImpl(@BuildEventsNls.Message String message,
                      @BuildEventsNls.Description String description,
                      @NotNull List<? extends Failure> causes,
                      @Nullable Throwable error,
                      @Nullable Notification notification,
                      @Nullable Navigatable navigatable) {
    myMessage = message;
    myDescription = description;
    myCauses = causes;
    myError = error;
    myNotification = notification;
    myNavigatable = navigatable;
  }

  @Override
  public @Nullable String getMessage() {
    return myMessage;
  }

  @Override
  public @Nullable String getDescription() {
    return myDescription;
  }

  @Override
  public @Nullable Throwable getError() {
    return myError;
  }

  @Override
  public List<? extends Failure> getCauses() {
    return myCauses;
  }

  @Override
  public @Nullable Notification getNotification() {
    return myNotification;
  }

  @Override
  public @Nullable Navigatable getNavigatable() {
    return myNavigatable;
  }
}
