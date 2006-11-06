/*
 * @author max
 */
package com.intellij;

import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.util.messages.Topic;

public class AppTopics {
  public static final Topic<FileTypeListener> FILE_TYPES = new Topic<FileTypeListener>("File types change", FileTypeListener.class);
}