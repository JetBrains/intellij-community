// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @Nullable
  private final Throwable myError;
  @Nullable
  private final Notification myNotification;
  @Nullable
  private final Navigatable myNavigatable;

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

  @Nullable
  @Override
  public String getMessage() {
    return myMessage;
  }

  @Nullable
  @Override
  public String getDescription() {
    return myDescription;
  }

  @Nullable
  @Override
  public Throwable getError() {
    return myError;
  }

  @Override
  public List<? extends Failure> getCauses() {
    return myCauses;
  }

  @Nullable
  @Override
  public Notification getNotification() {
    return myNotification;
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    return myNavigatable;
  }
}
