package org.jetbrains.io.jsonRpc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import io.netty.buffer.ByteBuf;
import org.jetbrains.io.webSocket.Client;
import org.jetbrains.io.webSocket.ExceptionHandler;
import org.jetbrains.io.webSocket.WebSocketServer;

import java.util.List;

public class IdeaAwareJsonRpcServer extends JsonRpcServer {
  private final QueueProcessor<ByteBuf> messageQueueProcessor = new QueueProcessor<ByteBuf>(new Consumer<ByteBuf>() {
    @Override
    public void consume(ByteBuf message) {
      IdeaAwareJsonRpcServer.super.doSend(-1, null, message);
    }
  });

  public IdeaAwareJsonRpcServer(WebSocketServer webSocketServer, ExceptionHandler exceptionHandler) {
    super(webSocketServer, exceptionHandler);
  }

  @Override
  protected <T> void doSend(int messageId, List<AsyncResult<Pair<Client, T>>> results, ByteBuf message) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      // it is bad idea to hide knowledge about real work thread from client of this rpc server, so, currently, it works only for particular case
      LOG.assertTrue(messageId == -1 && results == null);
      messageQueueProcessor.add(message);
    }
    else {
      super.doSend(messageId, results, message);
    }
  }
}
