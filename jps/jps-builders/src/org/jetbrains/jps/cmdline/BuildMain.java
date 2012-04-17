package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.api.SharedThreadPool;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/16/12
 */
public class BuildMain {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.BuildMain");

  public static void main(String[] args){
    final String host = args[0];
    final int port = Integer.parseInt(args[1]);
    final UUID sessionId = UUID.fromString(args[2]);
    final File systemDir = new File(args[3]);
    systemDir.mkdirs();

    final ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(SharedThreadPool.INSTANCE, SharedThreadPool.INSTANCE, 1));
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new ProtobufVarint32FrameDecoder(),
          new ProtobufDecoder(CmdlineRemoteProto.Message.getDefaultInstance()),
          new ProtobufVarint32LengthFieldPrepender(),
          new ProtobufEncoder(),
          new MyMessageHandler(sessionId)
        );
      }
    });
    bootstrap.setOption("tcpNoDelay", true);
    bootstrap.setOption("keepAlive", true);

    final ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.awaitUninterruptibly();

    final boolean success = future.isSuccess();

    if (success) {
      Channels.write(future.getChannel(), CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createParamRequest()));
    }
    else {
      final Throwable reason = future.getCause();
      if (reason != null) {
        System.err.println("Error connecting to " + host + ":" + port + "; " + reason.getMessage());
        reason.printStackTrace(System.err);
      }
      System.exit(-1);
    }
  }

  private static class MyMessageHandler extends SimpleChannelHandler {
    private final UUID mySessionId;
    private volatile BuildSession mySession;

    private MyMessageHandler(UUID sessionId) {
      mySessionId = sessionId;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      CmdlineRemoteProto.Message message = (CmdlineRemoteProto.Message)e.getMessage();
      final CmdlineRemoteProto.Message.Type type = message.getType();

      if (type == CmdlineRemoteProto.Message.Type.CONTROLLER_MESSAGE) {
        final CmdlineRemoteProto.Message.ControllerMessage controllerMessage = message.getControllerMessage();
        switch (controllerMessage.getType()) {

          case BUILD_PARAMETERS:
            if (mySession == null) {
              final BuildSession session = new BuildSession(mySessionId, ctx.getChannel(), controllerMessage.getParamsMessage());
              mySession = session;
              SharedThreadPool.INSTANCE.submit(session);
            }
            else {
              LOG.info("Cannot start another build session because one is already running");
            }
            return;

          case CANCEL_BUILD_COMMAND:
            final BuildSession session = mySession;
            if (session != null) {
              session.cancel();
            }
            else {
              LOG.info("Cannot cancel build: no build session is running");
              closeChannel(ctx.getChannel());
            }
            return;
        }
      }

      Channels.write(ctx.getChannel(), CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createFailure("Unsupported message type: " + type.name(), null)));
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      try {
        super.channelClosed(ctx, e);
      }
      finally {
        SharedThreadPool.INSTANCE.submit(new Runnable() {
          @Override
          public void run() {
            System.exit(0);
          }
        });
      }
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      try {
        super.channelDisconnected(ctx, e);
      }
      finally {
        closeChannel(ctx.getChannel());
      }
    }
  }

  private static void closeChannel(final Channel channel) {
    SharedThreadPool.INSTANCE.submit(new Runnable() {
      @Override
      public void run() {
        channel.close().awaitUninterruptibly();
      }
    });
  }
}
