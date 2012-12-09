package com.intellij.openapi.application;

public abstract class AccessToken {
  protected void acquired() {
  }

  protected void released() {
  }

  public abstract void finish();

  public static final AccessToken EMPTY_ACCESS_TOKEN = new AccessToken() {
    @Override
    public void finish() {}
  };
}
