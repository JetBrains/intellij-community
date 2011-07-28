package com.intellij.openapi.application;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.Topic;

/**
 * @author yole
 */
public interface ApplicationActivationListener {
  /**
   * Is called when app is activated by transferring focus to it
   * @param ideFrame
   */
  void applicationActivated(IdeFrame ideFrame);

  /**
   * Is called when app is de-activated by transferring focus from it
   * @param ideFrame
   */
  void applicationDeactivated(IdeFrame ideFrame);

  Topic<ApplicationActivationListener> TOPIC = Topic.create("com.intellij.openapi.application.ApplicationActivationListener", ApplicationActivationListener.class);
}
