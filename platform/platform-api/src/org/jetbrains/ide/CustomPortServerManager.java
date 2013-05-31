package org.jetbrains.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jboss.netty.channel.ChannelException;
import org.jetbrains.annotations.Nullable;

public abstract class CustomPortServerManager {
  public static final ExtensionPointName<CustomPortServerManager> EP_NAME = ExtensionPointName.create("com.intellij.customPortServerManager");

  public abstract void cannotBind(ChannelException e);

  public interface CustomPortService {
    boolean rebind();

    boolean isBound();
  }

  public abstract int getPort();

  public abstract void setManager(@Nullable CustomPortService manager);
}