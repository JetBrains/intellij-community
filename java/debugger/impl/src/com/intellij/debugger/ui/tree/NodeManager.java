package com.intellij.debugger.ui.tree;

import com.intellij.debugger.engine.evaluation.EvaluationContext;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface NodeManager {
  DebuggerTreeNode createMessageNode(String s);

  DebuggerTreeNode createNode(NodeDescriptor nodeDescriptor, EvaluationContext evaluationContext);
}
