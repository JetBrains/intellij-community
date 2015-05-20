package org.jetbrains.builtInWebServer;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.net.NetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.io.NettyUtil;

import java.net.InetSocketAddress;

public abstract class SingleConnectionNetService extends NetService {
  protected volatile Channel processChannel;

  protected SingleConnectionNetService(@NotNull Project project) {
    super(project);
  }

  protected abstract void configureBootstrap(@NotNull Bootstrap bootstrap, @NotNull Consumer<String> errorOutputConsumer);

  @Override
  protected void connectToProcess(@NotNull AsyncPromise<OSProcessHandler> promise, int port, @NotNull OSProcessHandler processHandler, @NotNull Consumer<String> errorOutputConsumer) {
    Bootstrap bootstrap = NettyUtil.oioClientBootstrap();
    configureBootstrap(bootstrap, errorOutputConsumer);
    Channel channel = NettyUtil.connect(bootstrap, new InetSocketAddress(NetUtils.getLoopbackAddress(), port), promise);
    if (channel != null) {
      processChannel = channel;
      promise.setResult(processHandler);
    }
  }

  @Override
  protected void closeProcessConnections() {
    Channel currentProcessChannel = processChannel;
    if (currentProcessChannel != null) {
      processChannel = null;
      NettyUtil.closeAndReleaseFactory(currentProcessChannel);
    }
  }
}