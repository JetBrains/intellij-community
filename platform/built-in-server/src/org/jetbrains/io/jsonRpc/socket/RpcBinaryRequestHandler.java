// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class RpcBinaryRequestHandler extends BinaryRequestHandler implements ExceptionHandler, ClientListener {
  private static final Logger LOG = Logger.getInstance(RpcBinaryRequestHandler.class);

  private static final UUID ID = UUID.fromString("69957EEB-AFB8-4036-A9A8-00D2D022F9BD");

  private final AtomicNotNullLazyValue<ClientManager> clientManager = new AtomicNotNullLazyValue<ClientManager>() {
    @NotNull
    @Override
    protected ClientManager compute() {
      ClientManager result = new ClientManager(RpcBinaryRequestHandler.this, RpcBinaryRequestHandler.this, null);
      Disposable serverDisposable = BuiltInServerManager.getInstance().getServerDisposable();
      assert serverDisposable != null;
      Disposer.register(serverDisposable, result);

      rpcServer = new JsonRpcServer(result);
      JsonRpcServerKt.registerFromEp(rpcServer);
      return result;
    }
  };

  private JsonRpcServer rpcServer;

  /**
   * For dynamic domain registration
   * @return
   */
  public static JsonRpcServer getRpcServerInstance() {
    return BinaryRequestHandler.EP_NAME.findExtension(RpcBinaryRequestHandler.class).getServer();
  }

  @NotNull
  private JsonRpcServer getServer() {
    clientManager.getValue();
    return rpcServer;
  }

  @NotNull
  @Override
  public UUID getId() {
    return ID;
  }

  @NotNull
  @Override
  public ChannelHandler getInboundHandler(@NotNull ChannelHandlerContext context) {
    SocketClient client = new SocketClient(context.channel());
    context.channel().attr(ClientManagerKt.getCLIENT()).set(client);
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

    private final SocketClient client;

    public MyDecoder(@NotNull SocketClient client) {
      this.client = client;
    }

    @Override
    protected void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) {
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
              rpcServer.messageReceived(client, content);
            }
            catch (Throwable e) {
              clientManager.getValue().getExceptionHandler().exceptionCaught(e);
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
    public void channelInactive(ChannelHandlerContext context) {
      Client client = context.channel().attr(ClientManagerKt.getCLIENT()).get();
      // if null, so, has already been explicitly removed
      if (client != null) {
        clientManager.getValue().disconnectClient(context.channel(), client, false);
      }
    }
  }
}
