// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.util.messages.Topic;

public interface DocInfoListener {
  Topic<DocInfoListener> TOPIC = new Topic<>(DocInfoListener.class.getSimpleName(),
                                             DocInfoListener.class);
  /**
   * invokes when quick documentation data is fetched and rendered on {@link DocumentationComponent}
   * @see DocumentationManager#doFetchDocInfo(DocumentationComponent, DocumentationManager.DocumentationCollector)
   */
  void onDocumentationRendered();
}