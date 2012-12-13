package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.log4j.Level;
import org.apache.log4j.xml.DOMConfigurator;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/16/12
 */
public class BuildMain {
  public static final Key<String> FORCE_MODEL_LOADING_PARAMETER = Key.create("_force_model_loading");
  private static final String LOG_FILE_NAME = "log.xml";
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.BuildMain");
  private static NioClientSocketChannelFactory ourChannelFactory;

  public static void main(String[] args){
    System.out.println("Build process started. Classpath: " + System.getProperty("java.class.path"));
    final String host = args[0];
    final int port = Integer.parseInt(args[1]);
    final UUID sessionId = UUID.fromString(args[2]);
    final File systemDir = new File(FileUtil.toCanonicalPath(args[3]));
    Utils.setSystemRoot(systemDir);

    initLoggers();

    ourChannelFactory = new NioClientSocketChannelFactory(SharedThreadPool.getInstance(), SharedThreadPool.getInstance(), 1);
    final ClientBootstrap bootstrap = new ClientBootstrap(ourChannelFactory);
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
      System.err.println("Error connecting to " + host + ":" + port + "; reason: " + (reason != null? reason.getMessage() : "unknown"));
      if (reason != null) {
        reason.printStackTrace(System.err);
      }
      System.err.println("Exiting.");
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
      final Channel channel = ctx.getChannel();

      if (type == CmdlineRemoteProto.Message.Type.CONTROLLER_MESSAGE) {
        final CmdlineRemoteProto.Message.ControllerMessage controllerMessage = message.getControllerMessage();
        switch (controllerMessage.getType()) {

          case BUILD_PARAMETERS: {
            if (mySession == null) {
              final CmdlineRemoteProto.Message.ControllerMessage.FSEvent delta = controllerMessage.hasFsEvent()? controllerMessage.getFsEvent() : null;
              final BuildSession session = new BuildSession(mySessionId, channel, controllerMessage.getParamsMessage(), delta);
              mySession = session;
              SharedThreadPool.getInstance().executeOnPooledThread(new Runnable() {
                public void run() {
                  try {
                    session.run();
                  }
                  finally {
                    channel.close();
                    System.exit(0);
                  }
                }
              });
            }
            else {
              LOG.info("Cannot start another build session because one is already running");
            }
            return;
          }

          case FS_EVENT: {
            final BuildSession session = mySession;
            if (session != null) {
              session.processFSEvent(controllerMessage.getFsEvent());
            }
            return;
          }

          case CONSTANT_SEARCH_RESULT: {
            final BuildSession session = mySession;
            if (session != null) {
              session.processConstantSearchResult(controllerMessage.getConstantSearchResult());
            }
            return;
          }

          case CANCEL_BUILD_COMMAND: {
            final BuildSession session = mySession;
            if (session != null) {
              session.cancel();
            }
            else {
              LOG.info("Cannot cancel build: no build session is running");
              channel.close();
            }
            return;
          }
        }
      }

      Channels.write(channel, CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createFailure("Unsupported message type: " + type.name(), null)));
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      try {
        super.channelClosed(ctx, e);
      }
      finally {
        new Thread("Shutdown thread") {
          public void run() {
            try {
              ourChannelFactory.releaseExternalResources();
            }
            finally {
              System.exit(0);
            }
          }
        }.start();
      }
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      try {
        super.channelDisconnected(ctx, e);
      }
      finally {
        ctx.getChannel().close();
      }
    }
  }

  private static void initLoggers() {
    if (new File(LOG_FILE_NAME).exists()) {
      DOMConfigurator.configure(LOG_FILE_NAME);
    }

    Logger.setFactory(new Logger.Factory() {
      @Override
      public Logger getLoggerInstance(String category) {
        final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(category);

        return new Logger() {
          @Override
          public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
          }

          @Override
          public void debug(@NonNls String message) {
            logger.debug(message);
          }

          @Override
          public void debug(@Nullable Throwable t) {
            logger.debug("", t);
          }

          @Override
          public void debug(@NonNls String message, @Nullable Throwable t) {
            logger.debug(message, t);
          }

          @Override
          public void error(@NonNls String message, @Nullable Throwable t, @NonNls String... details) {
            logger.debug(message, t);
          }

          @Override
          public void info(@NonNls String message) {
            logger.info(message);
          }

          @Override
          public void info(@NonNls String message, @Nullable Throwable t) {
            logger.info(message, t);
          }

          @Override
          public void warn(@NonNls String message, @Nullable Throwable t) {
            logger.warn(message, t);
          }

          @Override
          public void setLevel(Level level) {
            logger.setLevel(level);
          }
        };
      }
    });
  }

}
