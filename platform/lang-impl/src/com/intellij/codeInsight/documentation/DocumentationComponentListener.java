// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.messages.Topic;

import java.awt.*;

/**
 * @deprecated Unused in v2 implementation.
 */
@Deprecated
public interface DocumentationComponentListener {
  Topic<DocumentationComponentListener> TOPIC = new Topic<>(DocumentationComponentListener.class.getSimpleName(),
                                                            DocumentationComponentListener.class);
  /**
   * invokes when {@link DocumentationComponent} data is changed
   * @see DocumentationComponent#setDataInternal(SmartPsiElementPointer, String, Rectangle, String)
   */
  void onComponentDataChanged();
}