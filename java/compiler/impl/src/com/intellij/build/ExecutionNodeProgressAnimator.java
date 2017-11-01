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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class ExecutionNodeProgressAnimator implements Runnable, Disposable {
  private static final int FRAMES_COUNT = 8;
  private static final int MOVIE_TIME = 1200;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  public static final Icon[] FRAMES = new Icon[FRAMES_COUNT];

  private long myLastInvocationTime = -1;

  private Alarm myAlarm;
  private List<ExecutionNode> myNodes = new SmartList<>();
  private BuildTreeConsoleView myTreeView;

  public ExecutionNodeProgressAnimator(BuildTreeConsoleView treeConsoleView) {
    Disposer.register(treeConsoleView, this);
    myAlarm = new Alarm();
    myTreeView = treeConsoleView;
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

  public void run() {
    if (!myNodes.isEmpty()) {
      final long time = System.currentTimeMillis();
      // optimization:
      // we shouldn't repaint if this frame was painted in current interval
      if (time - myLastInvocationTime >= FRAME_TIME) {
        repaintTree();
        myLastInvocationTime = time;
      }
    }
    scheduleRepaint();
  }

  public void addNode(@Nullable final ExecutionNode currentNode) {
    myNodes.add(currentNode);
  }

  public void startMovie() {
    scheduleRepaint();
  }

  public void stopMovie() {
    repaintTree();

    // running nodes likely will not receive stop event yet after stop build event
    for (ExecutionNode node : myNodes) {
      node.setIconProvider(() -> AllIcons.Process.State.YellowStr);
      node.setEndTime(System.currentTimeMillis());
    }
    myNodes.clear();
    cancelAlarm();
  }

  public void dispose() {
    myTreeView = null;
    myNodes.clear();
    cancelAlarm();
  }

  private void cancelAlarm() {
    if (myAlarm != null) {
      myAlarm.cancelAllRequests();
      myAlarm = null;
    }
  }

  private void repaintTree() {
    if (myTreeView == null || myTreeView.isDisposed()) return;

    for (Iterator<ExecutionNode> iterator = myNodes.iterator(); iterator.hasNext(); ) {
      ExecutionNode node = iterator.next();
      myTreeView.scheduleUpdate(node);
      if (!node.isRunning()) {
        iterator.remove();
      }
    }
  }

  private void scheduleRepaint() {
    if (myAlarm == null) {
      return;
    }
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(this, FRAME_TIME);
  }
}
