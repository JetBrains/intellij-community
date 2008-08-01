package com.intellij.ui.content;

public interface MessageView  {

  ContentManager getContentManager();

  void runWhenInitialized(final Runnable runnable);
}
