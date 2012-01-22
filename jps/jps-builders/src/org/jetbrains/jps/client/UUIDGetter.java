package org.jetbrains.jps.client;

import org.jboss.netty.channel.MessageEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
* @author Eugene Zhuravlev
*         Date: 1/22/12
*/
public interface UUIDGetter {
  @NotNull UUID getSessionUUID(@NotNull MessageEvent e);
}
