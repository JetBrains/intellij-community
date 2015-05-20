package org.jetbrains.io.jsonRpc.socket;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Disposer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BinaryRequestHandler;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.io.MessageDecoder;
import org.jetbrains.io.jsonRpc.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class RpcBinaryRequestHandler extends BinaryRequestHandler implements ExceptionHandler, ClientListener {
  private static final Logger LOG = Logger.getInstance(RpcBinaryRequestHandler.class);

  private static final UUID ID = UUID.fromString("69957EEB-AFB8-4036-A9A8-00D2D022F9BD");

  private final AtomicNotNullLazyValue<ClientManager> clientManager = new AtomicNotNullLazyValue<ClientManager>() {
    @NotNull
    @Override
    protected ClientManager compute() {
      ClientManager result = new ClientManager(RpcBinaryRequestHandler.this, RpcBinaryRequestHandler.this);
      Disposable serverDisposable = BuiltInServerManager.getInstance().getServerDisposable();
      assert serverDisposable != null;
      Disposer.register(serverDisposable, result);

      rpcServer = new JsonRpcServer(result);
      return result;
    }
  };

  private JsonRpcServer rpcServer;

  @NotNull
  @Override
  public UUID getId() {
    return ID;
  }

  @NotNull
  @Override
  public ChannelHandler getInboundHandler(@NotNull ChannelHandlerContext context) {
    SocketClient client = new SocketClient(context.channel());
    context.attr(ClientManager.CLIENT).set(client);
    clientManager.getValue().addClient(client);
    connected(client, null);
    return new MyDecoder(client);
  }

  @Override
  public void exceptionCaught(@NotNull Throwable e) {
    LOG.error(e);
  }

  @Override
  public void connected(@NotNull Client client, @Nullable Map<String, List<String>> parameters) {
  }

  @Override
  public void disconnected(@NotNull Client client) {
  }

  private enum State {
    LENGTH, CONTENT
  }

  private class MyDecoder extends MessageDecoder {
    private State state = State.LENGTH;
    private int contentLength;

    private final SocketClient client;

    public MyDecoder(@NotNull SocketClient client) {
      this.client = client;
    }

    @Override
    protected void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) throws Exception {
      while (true) {
        switch (state) {
          case LENGTH: {
            ByteBuf buffer = getBufferIfSufficient(input, 4, context);
            if (buffer == null) {
              return;
            }

            state = State.CONTENT;
            contentLength = buffer.readInt();
          }

          case CONTENT: {
            CharSequence content = readChars(input);
            if (content == null) {
              return;
            }

            try {
              rpcServer.messageReceived(client, content, true);
            }
            catch (Throwable e) {
              clientManager.getValue().exceptionHandler.exceptionCaught(e);
            }
            finally {
              contentLength = 0;
              state = State.LENGTH;
            }
          }
        }
      }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
      Client client = context.attr(ClientManager.CLIENT).get();
      // if null, so, has already been explicitly removed
      if (client != null) {
        clientManager.getValue().disconnectClient(context, client, false);
      }
    }
  }
}
