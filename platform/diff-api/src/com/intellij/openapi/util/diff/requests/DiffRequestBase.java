package com.intellij.openapi.util.diff.requests;

import com.intellij.openapi.util.UserDataHolderBase;

public abstract class DiffRequestBase extends UserDataHolderBase implements DiffRequest {
  @Override
  public void onAssigned(boolean isAssigned) {
  }
}
