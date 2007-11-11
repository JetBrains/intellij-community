/*
 * @author max
 */
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Document;

public interface DocumentWindow extends Document {
  Document getDelegate();
}