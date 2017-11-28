// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Vladislav.Soroka
 */
public class BuildOutputInstantReaderImpl implements Appendable, Closeable, BuildOutputInstantReader {

  private static final int MAX_LINES_BUFFER_SIZE = 50;

  private final Object myBuildId;
  @Nullable
  private StringBuilder myBuffer;
  private final BlockingQueue<String> myQueue = new LinkedTransferQueue<>();

  private int myCurrentIndex = -1;
  private LinkedList<String> myLinesBuffer = new LinkedList<>();
  public static final String SHUTDOWN_PILL = new String("Poison Pill Shutdown");

  private Thread myThread;
  private final AtomicBoolean myStarted = new AtomicBoolean();
  private final AtomicBoolean myClosed = new AtomicBoolean();

  public BuildOutputInstantReaderImpl(@NotNull Object buildId,
                                      @NotNull BuildProgressListener buildProgressListener,
                                      @NotNull List<BuildOutputParser> parsers) {
    myBuildId = buildId;
    myThread = new Thread(() -> {
      Ref<MessageEvent> lastMessageRef = Ref.create();
      Consumer<MessageEvent> messageConsumer = event -> {
        //do not add duplicates, e.g. sometimes same messages can be added both to stdout and stderr
        if (!event.equals(lastMessageRef.get())) {
          buildProgressListener.onEvent(event);
        }
        lastMessageRef.set(event);
      };

      String line;
      while ((line = this.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        for (BuildOutputParser parser : parsers) {
          if (parser.parse(line, this, messageConsumer)) {
            break;
          }
        }
      }
    }, "Build output processor");
  }

  @Override
  public Object getBuildId() {
    return myBuildId;
  }

  @Override
  public Appendable append(CharSequence csq) {
    for (int i = 0; i < csq.length(); i++) {
      append(csq.charAt(i));
    }
    return this;
  }

  @Override
  public Appendable append(CharSequence csq, int start, int end) {
    append(csq.subSequence(start, end));
    return this;
  }

  @Override
  public Appendable append(char c) {
    if (myBuffer == null) {
      myBuffer = new StringBuilder();
    }
    if (c == '\n') {
      doFlush();
    }
    else {
      myBuffer.append(c);
    }
    return this;
  }

  @Override
  public void close() {
    doFlush();
    try {
      myQueue.put(SHUTDOWN_PILL);
      myThread.join(TimeUnit.MINUTES.toMillis(1));
    }
    catch (InterruptedException ignore) {
    }
    finally {
      myClosed.set(true);
    }
  }

  private void doFlush() {
    if (myBuffer == null) {
      return;
    }
    String line = myBuffer.toString();
    myBuffer.setLength(0);
    try {
      if (myStarted.compareAndSet(false, true)) {
        myThread.start();
      }
      myQueue.put(line);
    }
    catch (InterruptedException ignore) {
      myClosed.set(true);
    }
  }

  @Nullable
  @Override
  public String readLine() {
    if (myClosed.get()) return null;

    myCurrentIndex++;
    if (myLinesBuffer.size() > myCurrentIndex) {
      return myLinesBuffer.get(myCurrentIndex);
    }
    try {
      String line = myQueue.take();
      if (line == SHUTDOWN_PILL) {
        myClosed.set(true);
        return null;
      }
      myLinesBuffer.addLast(line);
      if (myLinesBuffer.size() > MAX_LINES_BUFFER_SIZE) {
        myLinesBuffer.removeFirst();
      }
      return line;
    }
    catch (InterruptedException ignore) {
      myClosed.set(true);
    }
    return null;
  }

  @Override
  public void pushBack() {
    myCurrentIndex--;
  }

  @Override
  public String getCurrentLine() {
    return myLinesBuffer.size() > myCurrentIndex ? myLinesBuffer.get(myCurrentIndex) : null;
  }
}
