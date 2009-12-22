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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.rt.execution.junit.segments.PacketProcessor;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * @author dyoma
 */
public class Extractor {
  private DeferredActionsQueue myFulfilledWorkGate = null;
  private final SegmentedInputStream myStream;

  public Extractor(final InputStream stream, final Charset charset) {
    myStream = new SegmentedInputStream(stream, charset);
  }

  public void setDispatchListener(final DispatchListener listener) {
    myFulfilledWorkGate.setDispactchListener(listener);
  }

  public void setPacketDispatcher(final PacketProcessor packetProcessor, final DeferredActionsQueue queue) {
    myFulfilledWorkGate = new DeferredActionsQueue() { //todo make it all later
      public void addLast(final Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            queue.addLast(runnable);
          }
        }, ModalityState.NON_MODAL);
      }

      public void setDispactchListener(final DispatchListener listener) {
        queue.setDispactchListener(listener);
      }
    };
    myStream.setEventsDispatcher(new PacketProcessor() {
      public void processPacket(final String packet) {
        myFulfilledWorkGate.addLast(new Runnable() {
          public void run() {
            packetProcessor.processPacket(packet);
          }
        });
      }
    });
  }

  public Reader createReader() {
    return new SegmentedInputStreamReader(myStream);
  }

}
