
package com.intellij.openapi.vfs;

import java.util.EventListener;

public interface VirtualFileManagerListener extends EventListener {
  void beforeRefreshStart(boolean asynchonous);

  void afterRefreshFinish(boolean asynchonous);
}