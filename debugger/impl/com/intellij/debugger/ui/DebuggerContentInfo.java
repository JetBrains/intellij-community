package com.intellij.debugger.ui;

import com.intellij.openapi.util.Key;

public interface DebuggerContentInfo {
  Key<Key> CONTENT_KIND = Key.create("ContentKind");
  Key CONSOLE_CONTENT = Key.create("ConsoleContent");
  Key THREADS_CONTENT = Key.create("ThreadsContent");
  Key VARIABLES_CONTENT = Key.create("VariablesContent");
  Key FRAME_CONTENT = Key.create("FrameContent");
  Key WATCHES_CONTENT = Key.create("WatchesContent");
  Key LOG_CONTENT = Key.create("LogContent");

}
