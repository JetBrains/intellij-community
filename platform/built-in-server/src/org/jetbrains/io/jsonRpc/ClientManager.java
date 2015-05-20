package org.jetbrains.io.jsonRpc;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SimpleTimerTask;
import gnu.trove.THashSet;
import gnu.trove.TObjectProcedure;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.io.webSocket.WebSocketServerOptions;

import java.util.List;

public class ClientManager implements Disposable {
  public static final AttributeKey<Client> CLIENT = AttributeKey.valueOf("SocketHandler.client");

  private final SimpleTimerTask heartbeatTimer;

  @Nullable
  private final ClientListener listener;

  @NotNull
  public final ExceptionHandler exceptionHandler;

  private final THashSet<Client> clients = new THashSet<Client>();

  public ClientManager() {
    this(null, new ExceptionHandlerImpl());
  }

  public ClientManager(@Nullable ClientListener listener, @NotNull ExceptionHandler exceptionHandler) {
    this(null, exceptionHandler, listener);
  }

  public ClientManager(@Nullable WebSocketServerOptions options, @NotNull ExceptionHandler exceptionHandler, @Nullable ClientListener listener) {
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

  public <T> void send(final int messageId, @NotNull final ByteBuf message, @Nullable final List<AsyncPromise<Pair<Client, T>>> results) {
    forEachClient(new TObjectProcedure<Client>() {
      private boolean first;

      @Override
      public boolean execute(final Client client) {
        try {
          AsyncPromise<Pair<Client, T>> result = client.send(messageId, first ? message : message.duplicate());
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

  public boolean disconnectClient(@NotNull ChannelHandlerContext context, @NotNull Client client, boolean closeChannel) {
    synchronized (clients) {
      if (!clients.remove(client)) {
        return false;
      }
    }

    try {
      context.attr(CLIENT).remove();

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