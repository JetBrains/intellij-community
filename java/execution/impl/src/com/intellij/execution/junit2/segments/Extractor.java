/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.junit2.segments;

import com.intellij.execution.junit.SegmentedInputStreamReader;
import com.intellij.execution.junit2.SegmentedInputStream;
import com.intellij.execution.testframework.Printable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;

/**
 * @author dyoma
 */
public class Extractor {
  private DeferredActionsQueue myFulfilledWorkGate = null;
  private final SegmentedInputStream myStream;
  private OutputPacketProcessor myEventsDispatcher;
  private static final Logger LOG = Logger.getInstance("#" + Extractor.class.getName());
  private final Executor myQueue2 = new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE);

  public Extractor(@NotNull InputStream stream, @NotNull Charset charset) {
    myStream = new SegmentedInputStream(stream, charset);
  }

  public void setDispatchListener(final DispatchListener listener) {
    myFulfilledWorkGate.setDispactchListener(listener);
  }

  public void setPacketDispatcher(@NotNull final PacketProcessor packetProcessor, final DeferredActionsQueue queue) {
    myFulfilledWorkGate = new DeferredActionsQueue() { //todo make it all later
      @Override
      public void addLast(final Runnable runnable) {
        scheduleTask(queue, runnable);
      }

      @Override
      public void setDispactchListener(final DispatchListener listener) {
        queue.setDispactchListener(listener);
      }
    };
    myEventsDispatcher = new OutputPacketProcessor() {
      @Override
      public void processPacket(final String packet) {
        myFulfilledWorkGate.addLast(new Runnable() {
          @Override
          public void run() {
            packetProcessor.processPacket(packet);
          }
        });
      }

      @Override
      public void processOutput(final Printable printable) {
        LOG.assertTrue(packetProcessor instanceof OutputPacketProcessor);
        myFulfilledWorkGate.addLast(new Runnable() {
          @Override
          public void run() {
            ((OutputPacketProcessor)packetProcessor).processOutput(printable);
          }
        });
      }
    };
    myStream.setEventsDispatcher(myEventsDispatcher);
  }

  private void scheduleTask(final DeferredActionsQueue queue, final Runnable task) {
    myQueue2.execute(new Runnable() {
      public void run() {
        // for some reason there is a requirement that this activity must be done from the swing thread
        // will be blocking one of pooled threads here, which is ok
        try {
          SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
              queue.addLast(task);
            }
          });
        }
        catch (Throwable e) {
          LOG.info("Task rejected: " + task, e);
        }
      }
    });
  }

  public OutputPacketProcessor getEventsDispatcher() {
    return myEventsDispatcher;
  }

  public Reader createReader() {
    return new SegmentedInputStreamReader(myStream);
  }

  public void addRequest(final Runnable runnable, final DeferredActionsQueue queue) {
    scheduleTask(queue, runnable);
  }
}
