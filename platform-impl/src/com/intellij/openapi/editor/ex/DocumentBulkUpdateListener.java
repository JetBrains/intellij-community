/*
 * @author max
 */
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.util.messages.Topic;

public interface DocumentBulkUpdateListener {
  Topic<DocumentBulkUpdateListener> TOPIC = Topic.create("Bulk document change notifcation like reformat, etc.", DocumentBulkUpdateListener.class);

  void updateStarted(Document doc);
  void updateFinished(Document doc);
}