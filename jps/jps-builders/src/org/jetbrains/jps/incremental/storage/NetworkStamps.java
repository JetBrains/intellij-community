package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.Pair;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/24/12
 */
public class NetworkStamps implements Timestamps{
  private final UUID mySessionId;
  private final Channel myChannel;

  public NetworkStamps(UUID sessionId, Channel channel) {
    mySessionId = sessionId;
    myChannel = channel;
  }

  @Override
  public void force() {
    // empty
  }

  // todo: add alarm to send events in bulk messages?

  @Override
  public void saveStamp(File file, long timestamp) throws IOException {
    final CmdlineRemoteProto.Message.BuilderMessage builderMessage = CmdlineProtoUtil.createTimestampsMessage(
      Collections.singleton(new Pair<File, Long>(file, timestamp)), Collections.<File>emptyList()
    );
    Channels.write(myChannel, CmdlineProtoUtil.toMessage(mySessionId, builderMessage));
  }

  @Override
  public void removeStamp(File file) throws IOException {
    final CmdlineRemoteProto.Message.BuilderMessage builderMessage = CmdlineProtoUtil.createTimestampsMessage(
      Collections.<Pair<File, Long>>emptyList(), Collections.singleton(file)
    );
    Channels.write(myChannel, CmdlineProtoUtil.toMessage(mySessionId, builderMessage));
  }

  @Override
  public void clean() throws IOException {
    Channels.write(myChannel, CmdlineProtoUtil.toMessage(mySessionId,
      CmdlineRemoteProto.Message.BuilderMessage.newBuilder().setType(CmdlineRemoteProto.Message.BuilderMessage.Type.CLEAN_TIMESTAMPS).build()
    ));
  }

  @Override
  public long getStamp(File file) throws IOException {
    throw new IOException("operation getStamp() is not supported");
  }
}
