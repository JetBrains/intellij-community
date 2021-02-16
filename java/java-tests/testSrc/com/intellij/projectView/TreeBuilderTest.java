// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class TreeBuilderTest extends LightPlatformTestCase {
  private static final Integer ROOT = new Integer(0);
  private static final boolean IS_FINITE = false;
  private AbstractTreeStructure myInfiniteTreeStructure = new AbstractTreeStructure() {
    @NotNull
    @Override
    public Object getRootElement() {
      return ROOT;
    }

    @Override
    public Object @NotNull [] getChildElements(@NotNull Object element) {
      int intValue = intValueOf(element);
      if (IS_FINITE && intValue == 2) {
        return new Object[]{};
      }
      return new Object[]{new Integer(intValue + 1)};
    }

    @Override
    public Object getParentElement(@NotNull Object element) {
      return element == ROOT ? null :
             new Integer(intValueOf(element) - 1);
    }

    @Override
    @NotNull
    public NodeDescriptor createDescriptor(@NotNull final Object element, NodeDescriptor parentDescriptor) {
      return new NodeDescriptor(null, parentDescriptor) {
        @Override
        public boolean update() {
          return false;
        }

        @Override
        public Object getElement() {
          return element;
        }
      };
    }

    @Override
    public void commit() {
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    private int intValueOf(Object element) {
      return ((Integer)element).intValue();
    }
  };

  @Override
  protected void tearDown() throws Exception {
    myInfiniteTreeStructure = null;
    super.tearDown();
  }

  public void test() {
    MyAbstractTreeBuilder treeBuilder = new MyAbstractTreeBuilder();
    try {
      treeBuilder.init();
    }
    finally {
      Disposer.dispose(treeBuilder);
    }
  }

  private class MyAbstractTreeBuilder extends AbstractTreeBuilder {
    MyAbstractTreeBuilder() {
      super(new JTree(), new DefaultTreeModel(new DefaultMutableTreeNode(ROOT)), myInfiniteTreeStructure, IndexComparator.INSTANCE);
    }

    @Override
    protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
      return nodeDescriptor.getElement() == ROOT;
    }

    @Override
    protected boolean isSmartExpand() {
      return false;
    }

    public void init() {
      initRootNode();
    }

    @Override
    @NotNull
    protected ProgressIndicator createProgressIndicator() {
      return new StatusBarProgress();
    }
  }
}
