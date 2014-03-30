/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.xml.DOMConfigurator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/16/12
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class BuildMain {
  private static final String LOG_CONFIG_FILE_NAME = "build-log.xml";
  private static final String LOG_FILE_NAME = "build.log";
  private static final String DEFAULT_LOGGER_CONFIG = "defaultLogConfig.xml";
  private static final String LOG_FILE_MACRO = "$LOG_FILE_PATH$";
  private static final Logger LOG;
  static {
    initLoggers();
    LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.BuildMain");
  }

  private static final int HOST_ARG = 0;
  private static final int PORT_ARG = HOST_ARG + 1;
  private static final int SESSION_ID_ARG = PORT_ARG + 1;
  private static final int SYSTEM_DIR_ARG = SESSION_ID_ARG + 1;

  private static NioEventLoopGroup ourEventLoopGroup;

  public static void main(String[] args){
    System.out.println("Build process started. Classpath: " + System.getProperty("java.class.path"));
    final String host = args[HOST_ARG];
    final int port = Integer.parseInt(args[PORT_ARG]);
    final UUID sessionId = UUID.fromString(args[SESSION_ID_ARG]);
    @SuppressWarnings("ConstantConditions")
    final File systemDir = new File(FileUtil.toCanonicalPath(args[SYSTEM_DIR_ARG]));
    Utils.setSystemRoot(systemDir);

    ourEventLoopGroup = new NioEventLoopGroup(1, SharedThreadPool.getInstance());
    final Bootstrap bootstrap = new Bootstrap().group(ourEventLoopGroup).channel(NioSocketChannel.class).handler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(new ProtobufVarint32FrameDecoder(),
                                   new ProtobufDecoder(CmdlineRemoteProto.Message.getDefaultInstance()),
                                   new ProtobufVarint32LengthFieldPrepender(),
                                   new ProtobufEncoder(),
                                   new MyMessageHandler(sessionId));
      }
    }).option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true);

    final ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port)).awaitUninterruptibly();
    final boolean success = future.isSuccess();
    if (success) {
      future.channel().writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createParamRequest()));
    }
    else {
      @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
      final Throwable reason = future.cause();
      System.err.println("Error connecting to " + host + ":" + port + "; reason: " + (reason != null? reason.getMessage() : "unknown"));
      if (reason != null) {
        reason.printStackTrace(System.err);
      }
      System.err.println("Exiting.");
      System.exit(-1);
    }
  }

  private static class MyMessageHandler extends SimpleChannelInboundHandler<CmdlineRemoteProto.Message> {
    private final UUID mySessionId;
    private volatile BuildSession mySession;

    private MyMessageHandler(UUID sessionId) {
      mySessionId = sessionId;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext context, CmdlineRemoteProto.Message message) throws Exception {
      final CmdlineRemoteProto.Message.Type type = message.getType();
      final Channel channel = context.channel();

      if (type == CmdlineRemoteProto.Message.Type.CONTROLLER_MESSAGE) {
        final CmdlineRemoteProto.Message.ControllerMessage controllerMessage = message.getControllerMessage();
        switch (controllerMessage.getType()) {

          case BUILD_PARAMETERS: {
            if (mySession == null) {
              final CmdlineRemoteProto.Message.ControllerMessage.FSEvent delta = controllerMessage.hasFsEvent()? controllerMessage.getFsEvent() : null;
              final BuildSession session = new BuildSession(mySessionId, channel, controllerMessage.getParamsMessage(), delta);
              mySession = session;
              SharedThreadPool.getInstance().executeOnPooledThread(new Runnable() {
                @Override
                public void run() {
                  //noinspection finally
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

      channel.writeAndFlush(
        CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createFailure("Unsupported message type: " + type.name(), null)));
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
      try {
        super.channelInactive(context);
      }
      finally {
        new Thread("Shutdown thread") {
          @Override
          public void run() {
            //noinspection finally
            try {
              ourEventLoopGroup.shutdownGracefully();
            }
            finally {
              System.exit(0);
            }
          }
        }.start();
      }
    }
  }

  private static void initLoggers() {
    try {
      final String logDir = System.getProperty(GlobalOptions.LOG_DIR_OPTION, null);
      final File configFile = logDir != null? new File(logDir, LOG_CONFIG_FILE_NAME) : new File(LOG_CONFIG_FILE_NAME);
      ensureLogConfigExists(configFile);
      String text = FileUtil.loadFile(configFile);
      final String logFile = logDir != null? new File(logDir, LOG_FILE_NAME).getAbsolutePath() : LOG_FILE_NAME;
      text = StringUtil.replace(text, LOG_FILE_MACRO, StringUtil.replace(logFile, "\\", "\\\\"));
      new DOMConfigurator().doConfigure(new StringReader(text), LogManager.getLoggerRepository());
    }
    catch (IOException e) {
      System.err.println("Failed to configure logging: ");
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
    }

    Logger.setFactory(MyLoggerFactory.class);
  }

  private static void ensureLogConfigExists(final File logConfig) throws IOException {
    if (!logConfig.exists()) {
      FileUtil.createIfDoesntExist(logConfig);
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      final InputStream in = BuildMain.class.getResourceAsStream("/" + DEFAULT_LOGGER_CONFIG);
      if (in != null) {
        try {
          final FileOutputStream out = new FileOutputStream(logConfig);
          try {
            FileUtil.copy(in, out);
          }
          finally {
            out.close();
          }
        }
        finally {
          in.close();
        }
      }
    }
  }

  private static class MyLoggerFactory implements Logger.Factory {
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
        public void error(@NonNls String message, @Nullable Throwable t, @NotNull @NonNls String... details) {
          logger.error(message, t);
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
  }
}
