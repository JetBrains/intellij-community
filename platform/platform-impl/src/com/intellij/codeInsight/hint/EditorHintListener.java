package com.intellij.codeInsight.hint;

import com.intellij.openapi.project.Project;
import com.intellij.ui.LightweightHint;
import com.intellij.util.messages.Topic;

/**
 * @author yole
 */
public interface EditorHintListener {
  Topic<EditorHintListener> TOPIC = Topic.create("Notification about showing editor hints", EditorHintListener.class);

  void hintShown(Project project, LightweightHint hint, int flags);
}