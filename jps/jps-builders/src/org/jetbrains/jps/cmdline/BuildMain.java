// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.TimeoutUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.PreloadedDataExtension;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.storage.BuildTargetsState;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class BuildMain {
  private static final String PRELOAD_PROJECT_PATH = "preload.project.path";
  private static final String PRELOAD_CONFIG_PATH = "preload.config.path";

  private static final Logger LOG;
  static {
    LogSetup.initLoggers();
    LOG = Logger.getInstance(BuildMain.class);
  }

  private static final int HOST_ARG = 0;
  private static final int PORT_ARG = HOST_ARG + 1;
  private static final int SESSION_ID_ARG = PORT_ARG + 1;
  private static final int SYSTEM_DIR_ARG = SESSION_ID_ARG + 1;

  private static NioEventLoopGroup ourEventLoopGroup;
  @Nullable
  private static PreloadedData ourPreloadedData;

  public static void main(String[] args) {
    try {
      final long processStart = System.nanoTime();
      final String startMessage = "Build process started. Classpath: " + System.getProperty("java.class.path");
      System.out.println(startMessage);
      LOG.info("==================================================");
      LOG.info(startMessage);

      final String host = args[HOST_ARG];
      final int port = Integer.parseInt(args[PORT_ARG]);
      final UUID sessionId = UUID.fromString(args[SESSION_ID_ARG]);
      final File systemDir = new File(FileUtilRt.toCanonicalPath(args[SYSTEM_DIR_ARG], File.separatorChar, true));
      Utils.setSystemRoot(systemDir);

      final long connectStart = System.nanoTime();
      // IDEA-123132, let's try again
      for (int attempt = 0; attempt < 3; attempt++) {
        try {
          ourEventLoopGroup = new NioEventLoopGroup(1, (ThreadFactory)r -> new Thread(r, "JPS event loop"));
          break;
        }
        catch (IllegalStateException e) {
          if (attempt == 2) {
            printErrorAndExit(host, port, e);
            return;
          }
          else {
            LOG.warn("Cannot create event loop, attempt #" + attempt, e);
            TimeoutUtil.sleep(10 * (attempt + 1));
          }
        }
      }

      final Bootstrap bootstrap = new Bootstrap().group(ourEventLoopGroup).channel(NioSocketChannel.class).handler(new ChannelInitializer() {
        @Override
        protected void initChannel(Channel channel) {
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
        LOG.info("Connection to IDE established in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart) + " ms");

        final String projectPathToPreload = System.getProperty(PRELOAD_PROJECT_PATH, null);
        final String globalsPathToPreload = System.getProperty(PRELOAD_CONFIG_PATH, null);
        if (projectPathToPreload != null && globalsPathToPreload != null) {
          final PreloadedData data = new PreloadedData();
          ourPreloadedData = data;
          try {
            FileSystemUtil.getAttributes(projectPathToPreload); // this will pre-load all FS optimizations

            final BuildRunner runner = new BuildRunner(new JpsModelLoaderImpl(projectPathToPreload, globalsPathToPreload, false, null));
            data.setRunner(runner);

            final File dataStorageRoot = Utils.getDataStorageRoot(projectPathToPreload);
            final BuildFSState fsState = new BuildFSState(false);
            final ProjectDescriptor pd = runner.load(new MessageHandler() {
              @Override
              public void processMessage(BuildMessage msg) {
                data.addMessage(msg);
              }
            }, dataStorageRoot, fsState);
            data.setProjectDescriptor(pd);

            final File fsStateFile = new File(dataStorageRoot, BuildSession.FS_STATE_FILE);
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(fsStateFile)))) {
              final int version = in.readInt();
              if (version == BuildFSState.VERSION) {
                final long savedOrdinal = in.readLong();
                final boolean hasWorkToDo = in.readBoolean();// must skip "has-work-to-do" flag
                fsState.load(in, pd.getModel(), pd.getBuildRootIndex());
                data.setFsEventOrdinal(savedOrdinal);
                data.setHasHasWorkToDo(hasWorkToDo);
              }
            }
            catch (FileNotFoundException ignored) {
            }
            catch (IOException e) {
              LOG.info("Error pre-loading FS state", e);
              fsState.clearAll();
            }

            // preloading target configurations and pre-calculating target dirty state
            final BuildTargetsState targetsState = pd.getTargetsState();
            for (BuildTarget<?> target : pd.getBuildTargetIndex().getAllTargets()) {
              targetsState.getTargetConfiguration(target).isTargetDirty(pd);
            }

            //noinspection ResultOfMethodCallIgnored
            BuilderRegistry.getInstance();

            JpsServiceManager.getInstance().getExtensions(PreloadedDataExtension.class).forEach(ext-> ext.preloadData(data));

            LOG.info("Pre-loaded process ready in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - processStart) + " ms");
          }
          catch (Throwable e) {
            LOG.info("Failed to pre-load project " + projectPathToPreload, e);
            // just failed to preload the project, the situation will be handled later, when real build starts
          }
        }
        else if (projectPathToPreload != null || globalsPathToPreload != null){
          LOG.info("Skipping project pre-loading step: both paths to project configuration files and path to global settings must be specified");
        }
        future.channel().writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createParamRequest()));
      }
      else {
        printErrorAndExit(host, port, future.cause());
      }
    }
    catch (Throwable e) {
      LOG.error(e);
      throw e;
    }
  }

  private static void printErrorAndExit(String host, int port, Throwable reason) {
    System.err.println("Error connecting to " + host + ":" + port + "; reason: " + (reason != null ? reason.getMessage() : "unknown"));
    if (reason != null) {
      reason.printStackTrace(System.err);
    }
    System.err.println("Exiting.");
    System.exit(-1);
  }

  private static final class MyMessageHandler extends SimpleChannelInboundHandler<CmdlineRemoteProto.Message> {
    private final UUID mySessionId;
    private volatile BuildSession mySession;

    private MyMessageHandler(UUID sessionId) {
      mySessionId = sessionId;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext context, CmdlineRemoteProto.Message message) {
      final CmdlineRemoteProto.Message.Type type = message.getType();
      final Channel channel = context.channel();

      if (type == CmdlineRemoteProto.Message.Type.CONTROLLER_MESSAGE) {
        final CmdlineRemoteProto.Message.ControllerMessage controllerMessage = message.getControllerMessage();
        switch (controllerMessage.getType()) {

          case BUILD_PARAMETERS: {
            if (mySession == null) {
              final CmdlineRemoteProto.Message.ControllerMessage.FSEvent delta = controllerMessage.hasFsEvent()? controllerMessage.getFsEvent() : null;
              final BuildSession session = new BuildSession(mySessionId, channel, controllerMessage.getParamsMessage(), delta, ourPreloadedData);
              mySession = session;
              SharedThreadPool.getInstance().execute(() -> {
                //noinspection finally
                try {
                  try {
                    session.run();
                  }
                  finally {
                    channel.close();
                  }
                }
                finally {
                  System.exit(0);
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
          case AUTHENTICATION_TOKEN: {
            CmdlineRemoteProto.Message.ControllerMessage.RequestParams requestParams = controllerMessage.getRequestParams();
            System.out.println("Got request params: " + requestParams.getAuthHeadersMap());
            return;
          }
          case CONSTANT_SEARCH_RESULT: {
            // ignored, functionality deprecated
            return;
          }

          case CANCEL_BUILD_COMMAND: {
            final BuildSession session = mySession;
            if (session != null) {
              session.cancel();
            }
            else {
              LOG.info("Build canceled, but no build session is running. Exiting.");
              try {
                final CmdlineRemoteProto.Message.BuilderMessage canceledEvent = CmdlineProtoUtil
                  .createBuildCompletedEvent("build completed", CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.CANCELED);
                channel.writeAndFlush(CmdlineProtoUtil.toMessage(mySessionId, canceledEvent)).await();
                channel.close();
              }
              catch (Throwable e) {
                LOG.info(e);
              }
              Thread.interrupted(); // to clear 'interrupted' flag
              final PreloadedData preloaded = ourPreloadedData;
              final ProjectDescriptor pd = preloaded != null? preloaded.getProjectDescriptor() : null;
              if (pd != null) {
                pd.release();
              }

              JpsServiceManager.getInstance().getExtensions(PreloadedDataExtension.class).forEach(ext-> ext.discardPreloadedData(preloaded));

              System.exit(0);
            }
            return;
          }
        }
      }

      channel.writeAndFlush(
        CmdlineProtoUtil.toMessage(mySessionId,
                                   CmdlineProtoUtil.createFailure(JpsBuildBundle.message("build.message.unsupported.message.type.0", type.name()), null)));
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
              ourEventLoopGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS);
            }
            finally {
              System.exit(0);
            }
          }
        }.start();
      }
    }
  }
}