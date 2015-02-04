package org.jetbrains.io.webSocket;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SimpleTimerTask;
import gnu.trove.THashSet;
import gnu.trove.TObjectProcedure;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WebSocketServer implements Disposable {
  private final SimpleTimerTask heartbeatTimer;

  @Nullable
  private final WebSocketServerListener listener;

  final ExceptionHandler exceptionHandler;

  private final THashSet<Client> clients = new THashSet<Client>();

  public WebSocketServer() {
    this(null, new ExceptionHandlerImpl());
  }

  @SuppressWarnings("UnusedDeclaration")
  public WebSocketServer(@Nullable WebSocketServerListener listener, @NotNull ExceptionHandler exceptionHandler) {
    this(null, exceptionHandler, listener);
  }

  public WebSocketServer(@Nullable WebSocketServerOptions options, @NotNull ExceptionHandler exceptionHandler, @Nullable WebSocketServerListener listener) {
    this.exceptionHandler = exceptionHandler;
    this.listener = listener;

    heartbeatTimer = SimpleTimer.getInstance().setUp(new Runnable() {
      @Override
      public void run() {
        synchronized (clients) {
          if (clients.isEmpty()) {
            return;
          }

          clients.forEach(new TObjectProcedure<Client>() {
            @Override
            public boolean execute(Client client) {
              if (client.channel.isActive()) {
                client.sendHeartbeat();
              }
              return true;
            }
          });
        }
      }
    }, (options == null ? new WebSocketServerOptions() : options).heartbeatDelay);
  }

  public void addClient(@NotNull Client client) {
    synchronized (clients) {
      clients.add(client);
    }
  }

  public int getClientCount() {
    synchronized (clients) {
      return clients.size();
    }
  }

  public boolean hasClients() {
    return getClientCount() > 0;
  }

  @Override
  public void dispose() {
    try {
      heartbeatTimer.cancel();
    }
    finally {
      synchronized (clients) {
        clients.clear();
      }
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void sendResponse(@NotNull Client client, @NotNull ByteBuf message) {
    if (client.channel.isOpen()) {
      client.send(message);
    }
  }

  @Nullable
  public <T> AsyncResult<T> send(Client client, int messageId, ByteBuf message) {
    try {
      return client.send(messageId, message);
    }
    catch (Throwable e) {
      exceptionHandler.exceptionCaught(e);
      return null;
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  public AsyncResult removeAsyncResult(Client client, int messageId) {
    return client.messageCallbackMap.remove(messageId);
  }

  public <T> void send(final int messageId, final ByteBuf message, @Nullable final List<AsyncResult<Pair<Client, T>>> results) {
    forEachClient(new TObjectProcedure<Client>() {
      private boolean first;

      @Override
      public boolean execute(final Client client) {
        try {
          AsyncResult<Pair<Client, T>> result = client.send(messageId, first ? message : message.duplicate());
          first = false;
          if (results != null) {
            results.add(result);
          }
        }
        catch (Throwable e) {
          exceptionHandler.exceptionCaught(e);
        }
        return true;
      }
    });
  }

  boolean disconnectClient(@NotNull ChannelHandlerContext context, @NotNull Client client, boolean closeChannel) {
    synchronized (clients) {
      if (!clients.remove(client)) {
        return false;
      }
    }

    try {
      context.attr(WebSocketHandshakeHandler.CLIENT).set(null);

      if (closeChannel) {
        context.channel().close();
      }

      client.rejectAsyncResults(exceptionHandler);
    }
    finally {
      if (listener != null) {
        listener.disconnected(client);
      }
    }
    return true;
  }

  public void forEachClient(@NotNull TObjectProcedure<Client> procedure) {
    synchronized (clients) {
      if (clients.isEmpty()) {
        return;
      }

      clients.forEach(procedure);
    }
  }
}