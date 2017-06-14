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
package com.intellij.build;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 */
public class ExecutionNodeProgressAnimator implements Runnable, Disposable {
  private static final int FRAMES_COUNT = 8;
  private static final int MOVIE_TIME = 1600;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  public static final Icon[] FRAMES = new Icon[FRAMES_COUNT];

  private long myLastInvocationTime = -1;

  private Alarm myAlarm;
  private ExecutionNode myCurrentNode;
  private AbstractTreeBuilder myTreeBuilder;

  public ExecutionNodeProgressAnimator(AbstractTreeBuilder builder) {
    Disposer.register(builder, this);
    init(builder);
  }

  static {
    FRAMES[0] = AllIcons.Process.State.GreyProgr_1;
    FRAMES[1] = AllIcons.Process.State.GreyProgr_2;
    FRAMES[2] = AllIcons.Process.State.GreyProgr_3;
    FRAMES[3] = AllIcons.Process.State.GreyProgr_4;
    FRAMES[4] = AllIcons.Process.State.GreyProgr_5;
    FRAMES[5] = AllIcons.Process.State.GreyProgr_6;
    FRAMES[6] = AllIcons.Process.State.GreyProgr_7;
    FRAMES[7] = AllIcons.Process.State.GreyProgr_8;
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

  public SimpleNode getCurrentNode() {
    return myCurrentNode;
  }

  public void run() {
    if (myCurrentNode != null) {
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

  public void setCurrentNode(@Nullable final ExecutionNode node) {
    myCurrentNode = node;
    scheduleRepaint();
  }

  public void stopMovie() {
    repaintSubTree();
    setCurrentNode(null);
    cancelAlarm();
  }


  public void dispose() {
    myTreeBuilder = null;
    myCurrentNode = null;
    cancelAlarm();
  }

  private void cancelAlarm() {
    if (myAlarm != null) {
      myAlarm.cancelAllRequests();
      myAlarm = null;
    }
  }

  private void repaintSubTree() {
    if (myTreeBuilder != null && myCurrentNode != null && myCurrentNode.isRunning()) {
      myTreeBuilder.queueUpdateFrom(myCurrentNode, false, false);
    }
  }


  private void scheduleRepaint() {
    if (myAlarm == null) {
      return;
    }
    myAlarm.cancelAllRequests();
    if (myCurrentNode != null) {
      myAlarm.addRequest(this, FRAME_TIME);
    }
  }
}
