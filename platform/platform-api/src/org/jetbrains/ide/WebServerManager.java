package org.jetbrains.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.Consumer;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;

public abstract class WebServerManager implements ApplicationComponent {
  // Your handler will be instantiated on first user request
  public static final ExtensionPointName<Consumer<ChannelPipeline>> EP_NAME =
    ExtensionPointName.create("com.intellij.serverPipelineConsumer");

  public static WebServerManager getInstance() {
    return ApplicationManager.getApplication().getComponent(WebServerManager.class);
  }

  public abstract int getPort();

  public abstract void addClosingListener(ChannelFutureListener listener);
}
