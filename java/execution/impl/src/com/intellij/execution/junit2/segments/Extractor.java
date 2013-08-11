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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author dyoma
 */
public class Extractor implements Disposable {
  private DeferredActionsQueue myFulfilledWorkGate = null;
  private final SegmentedInputStream myStream;
  private OutputPacketProcessor myEventsDispatcher;
  private static final Logger LOG = Logger.getInstance("#" + Extractor.class.getName());
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("Test Extractor", 20, true, MergingUpdateQueue.ANY_COMPONENT);
  private AtomicInteger myOrder = new AtomicInteger(-1);

  public Extractor(@NotNull InputStream stream, @NotNull Charset charset) {
    myStream = new SegmentedInputStream(stream, charset);
    myQueue.setPassThrough(false);//should be updated in awt thread
  }

  @Override
  public void dispose() {
    myQueue.flush();
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        Disposer.dispose(myQueue);
      }
    });
  }

  public void setDispatchListener(final DispatchListener listener) {
    myFulfilledWorkGate.setDispactchListener(listener);
  }

  public void setPacketDispatcher(@NotNull final PacketProcessor packetProcessor, final DeferredActionsQueue queue) {
    myFulfilledWorkGate = new DeferredActionsQueue() { //todo make it all later
      @Override
      public void addLast(final Runnable runnable) {
        myQueue.queue(new MyUpdate(runnable, queue, myOrder.incrementAndGet()));
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

  public OutputPacketProcessor getEventsDispatcher() {
    return myEventsDispatcher;
  }

  public Reader createReader() {
    return new SegmentedInputStreamReader(myStream);
  }

  public void addRequest(Runnable runnable, final DeferredActionsQueue queue) {
    myQueue.queue(new MyUpdate(runnable, queue, myOrder.incrementAndGet()));
  }

  private static class MyUpdate extends Update {
    private final Runnable myRunnable;
    private final DeferredActionsQueue myQueue;
    private final int myOrder;

    public MyUpdate(Runnable runnable, DeferredActionsQueue queue, int order) {
      super(runnable);
      myRunnable = runnable;
      myQueue = queue;
      myOrder = order;
    }

    @Override
    public void run() {
      myQueue.addLast(myRunnable);
    }

    @Override
    public int compareTo(Object o) {
      return myOrder - ((MyUpdate)o).myOrder;
    }
  }
}
