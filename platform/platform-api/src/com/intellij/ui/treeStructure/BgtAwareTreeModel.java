// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure;

import javax.swing.tree.TreeModel;

/**
 * Marker interface to indicate that the model can load nodes in the background without freezing the EDT
 */
public interface BgtAwareTreeModel extends TreeModel { }
