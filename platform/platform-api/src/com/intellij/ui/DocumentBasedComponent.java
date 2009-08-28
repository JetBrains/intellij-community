package com.intellij.ui;

import com.intellij.openapi.editor.Document;

/**
 * @author Roman Chernyatchik
 *
 * Component based on document. 
 * User activity watcher listens changes in documents of such components.
 */
public interface DocumentBasedComponent extends TextComponent {
  Document getDocument();
}
