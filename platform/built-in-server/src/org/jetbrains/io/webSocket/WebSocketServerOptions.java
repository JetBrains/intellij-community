package org.jetbrains.io.webSocket;

import com.intellij.util.Time;

public final class WebSocketServerOptions {
  // client will disconnect if server doesn't response (i.e. doesn't send any message or special heartbeat packet to client) in specified time
  public int heartbeatDelay = 25 * Time.SECOND;

  public int closeTimeout = 60 * 1000;

  public WebSocketServerOptions heartbeatDelay(int value) {
    heartbeatDelay = value;
    return this;
  }

  public WebSocketServerOptions closeTimeout(int value) {
    closeTimeout = value;
    return this;
  }
}
