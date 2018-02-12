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
package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.util.function.LongConsumer;

import static java.awt.EventQueue.invokeLater;
import static java.lang.System.nanoTime;

public class TransferToEDTQueueTestAction extends DumbAwareAction {
  private JFrame frame;

  public TransferToEDTQueueTestAction() {
    super("Test TransferToEDTQueue performance");
  }

  public void actionPerformed(AnActionEvent event) {
    if (frame == null) {
      JLabel label = new JLabel("Set a threshold in ms to join subtasks");
      label.setBorder(JBUI.Borders.empty(0, 5));

      SpinnerNumberModel model = new SpinnerNumberModel(0, 0, 200, 1);
      JSpinner spinner = new JSpinner(model);

      JButton button = new JButton("Start");
      button.addActionListener(started -> {
        button.setEnabled(false);
        spinner.setEnabled(false);
        label.setText("Task started");
        new Task(1000L, model.getNumber().intValue(), time -> {
          label.setText("Task done in " + time + " ms");
          spinner.setEnabled(true);
          button.setEnabled(true);
        }).run();
      });
      int ten = JBUI.scale(10);
      JPanel panel = new JPanel(new BorderLayout(ten, 0));
      panel.setBorder(JBUI.Borders.empty(10));
      panel.add(BorderLayout.CENTER, spinner);
      panel.add(BorderLayout.EAST, button);
      frame = new JFrame(event.getPresentation().getText());
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      frame.add(BorderLayout.CENTER, panel);
      frame.add(BorderLayout.SOUTH, label);
      frame.setAlwaysOnTop(true);
      frame.setResizable(false);
      frame.pack();
    }
    frame.setVisible(true);
    frame.requestFocus();
  }

  private static final class Task implements Runnable {
    private final TransferToEDTQueue<Runnable> queue;
    private final LongConsumer consumer;
    private final long startTime;
    private final long blockTime;
    private long blockCount;

    Task(long blockTime, int threshold, @NotNull LongConsumer consumer) {
      this.queue = threshold <= 0 ? null : TransferToEDTQueue.createRunnableMerger("TestAction", threshold);
      this.consumer = consumer;
      this.startTime = nanoTime();
      this.blockTime = blockTime;
      this.blockCount = 5_000_000_000L / blockTime;
    }

    @Override
    public void run() {
      blockCount--;
      long started = nanoTime();
      while (true) {
        long duration = nanoTime() - started;
        if (duration > blockTime) break;
      }
      if (blockCount <= 0) {
        consumer.accept((nanoTime() - this.startTime) / 1_000_000);
      }
      else if (queue != null) {
        queue.offer(this);
      }
      else {
        invokeLater(this);
      }
    }
  }
}
