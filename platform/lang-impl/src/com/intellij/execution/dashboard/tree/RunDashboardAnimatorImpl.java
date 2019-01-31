/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.dashboard.RunDashboardAnimator;
import com.intellij.execution.dashboard.RunDashboardNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.AnimatedIcon;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardAnimatorImpl implements RunDashboardAnimator, Runnable, Disposable {
  public static final Icon[] FRAMES = AnimatedIcon.Default.ICONS.toArray(new Icon[0]);
  private static final int FRAME_TIME = AnimatedIcon.Default.DELAY;
  private static final int MOVIE_TIME = FRAME_TIME * FRAMES.length;

  private long myLastInvocationTime = -1;

  private Alarm myAlarm;
  private final Set<RunDashboardNode> myNodes = new HashSet<>();
  private AbstractTreeBuilder myTreeBuilder;

  public RunDashboardAnimatorImpl(AbstractTreeBuilder builder) {
    Disposer.register(builder, this);
    init(builder);
  }

  public static int getCurrentFrameIndex() {
    return (int)((System.currentTimeMillis() % MOVIE_TIME) / FRAME_TIME);
  }

  public static Icon getCurrentFrame() {
    return FRAMES[getCurrentFrameIndex()];
  }

  /**
   * Initializes animator: creates alarm and sets tree builder
   *
   * @param treeBuilder tree builder
   */
  protected void init(final AbstractTreeBuilder treeBuilder) {
    myAlarm = new Alarm();
    myTreeBuilder = treeBuilder;
  }

  @Override
  public void run() {
    if (!myNodes.isEmpty()) {
      final long time = System.currentTimeMillis();
      // optimization:
      // we shouldn't repaint if this frame was painted in current interval
      if (time - myLastInvocationTime >= FRAME_TIME) {
        repaintSubTree();
        myLastInvocationTime = time;
      }
    }
    scheduleRepaint();
  }

  @Override
  public void addNode(@NotNull RunDashboardNode node) {
    if (myNodes.add(node) && myNodes.size() == 1) {
      scheduleRepaint();
    }
  }

  @Override
  public void removeNode(@NotNull RunDashboardNode node) {
    if (myNodes.remove(node) && myNodes.isEmpty()) {
      repaintSubTree();
      if (myAlarm != null) {
        myAlarm.cancelAllRequests();
      }
    }
  }

  @Override
  public void dispose() {
    myTreeBuilder = null;
    myNodes.clear();
    if (myAlarm != null) {
      myAlarm.cancelAllRequests();
      myAlarm = null;
    }
  }

  private void repaintSubTree() {
    if (myTreeBuilder == null || myTreeBuilder.isDisposed()) return;

    List<RunDashboardNode> toRemove = ContainerUtil.newSmartList();
    for (RunDashboardNode node : myNodes) {
      DefaultMutableTreeNode treeNode = myTreeBuilder.getUi().getNodeForElement(node, false);
      if (treeNode != null) {
        myTreeBuilder.queueUpdateFrom(node, false, false);
      }
      else {
        toRemove.add(node);
      }
    }
    myNodes.removeAll(toRemove);
  }

  private void scheduleRepaint() {
    if (myAlarm == null) {
      return;
    }
    myAlarm.cancelAllRequests();
    if (!myNodes.isEmpty()) {
      myAlarm.addRequest(this, FRAME_TIME);
    }
  }
}

