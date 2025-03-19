// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

public interface OccurenceNavigator extends ActionUpdateThreadAware {
  OccurenceNavigator EMPTY = new OccurenceNavigator() {
    @Override
    public boolean hasNextOccurence() {
      return false;
    }

    @Override
    public boolean hasPreviousOccurence() {
      return false;
    }

    @Override
    public OccurenceInfo goNextOccurence() {
      return null;
    }

    @Override
    public OccurenceInfo goPreviousOccurence() {
      return null;
    }

    @Override
    public @NotNull String getNextOccurenceActionName() {
      return "";
    }

    @Override
    public @NotNull String getPreviousOccurenceActionName() {
      return "";
    }
  };

  class OccurenceInfo {
    private final Navigatable myNavigateable;
    private final int myOccurenceNumber;
    private final int myOccurencesCount;

    public OccurenceInfo(Navigatable navigateable, int occurenceNumber, int occurencesCount) {
      myNavigateable = navigateable;
      myOccurenceNumber = occurenceNumber;
      myOccurencesCount = occurencesCount;
    }

    private OccurenceInfo(int occurenceNumber, int occurencesCount) {
      this(null, occurenceNumber, occurencesCount);
    }

    public static OccurenceInfo position(int occurenceNumber, int occurencesCount) {
      return new OccurenceInfo(occurenceNumber, occurencesCount);
    }

    public Navigatable getNavigateable() {
      return myNavigateable;
    }

    public int getOccurenceNumber() {
      return myOccurenceNumber;
    }

    public int getOccurencesCount() {
      return myOccurencesCount;
    }
  }

  boolean hasNextOccurence();
  boolean hasPreviousOccurence();
  OccurenceInfo goNextOccurence();
  OccurenceInfo goPreviousOccurence();
  @ActionText
  @NotNull
  String getNextOccurenceActionName();
  @ActionText
  @NotNull
  String getPreviousOccurenceActionName();

  @Override
  default @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
