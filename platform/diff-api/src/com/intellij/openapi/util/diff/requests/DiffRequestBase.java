package com.intellij.openapi.util.diff.requests;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DiffRequestBase extends UserDataHolderBase implements DiffRequest {
  @Override
  public void onAssigned(boolean isAssigned) {
  }
}
