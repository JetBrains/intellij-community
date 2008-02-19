package com.intellij.debugger.ui;

import com.intellij.openapi.util.Key;

public interface DebuggerContentInfo {
  Key<String> CONTENT_TYPE = Key.create("ContentType");

  String CONSOLE_CONTENT = "ConsoleContent";
  String THREADS_CONTENT = "ThreadsContent";
  String VARIABLES_CONTENT = "VariablesContent";
  String FRAME_CONTENT = "FrameContent";
  String WATCHES_CONTENT = "WatchesContent";

}
