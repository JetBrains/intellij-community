/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.client;

import com.google.protobuf.MessageLite;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.javac.JavacRemoteProto;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
* @author Eugene Zhuravlev
*/
@ChannelHandler.Sharable
final class ProtobufClientMessageHandler<T extends ProtobufResponseHandler> extends SimpleChannelInboundHandler<MessageLite> {
  private final ConcurrentMap<UUID, RequestFuture<T>> myHandlers = new ConcurrentHashMap<>(16, 0.75f, 1);
  @NotNull
  private final UUIDGetter myUuidGetter;
  private final SimpleProtobufClient myClient;
  private final Executor myAsyncExec;

  public ProtobufClientMessageHandler(@NotNull UUIDGetter uuidGetter, SimpleProtobufClient client, Executor asyncExec) {
    myUuidGetter = uuidGetter;
    myClient = client;
    myAsyncExec = asyncExec;
  }

  @Override
  public final void channelRead0(ChannelHandlerContext context, MessageLite message) throws Exception {
    final UUID messageUUID = myUuidGetter.getSessionUUID((JavacRemoteProto.Message)message);
    final RequestFuture<T> future = myHandlers.get(messageUUID);
    final T handler = future != null ? future.getMessageHandler() : null;
    if (handler == null) {
      terminateSession(messageUUID);
    }
    else {
      boolean terminateSession = false;
      try {
        terminateSession = handler.handleMessage(message);
      }
      catch (Exception ex) {
        terminateSession = true;
        throw ex;
      }
      finally {
        if (terminateSession) {
          terminateSession(messageUUID);
        }
      }
    }
  }

  private void terminateSession(UUID sessionId) {
    final RequestFuture<T> future = removeFuture(sessionId);
    if (future != null) {
      final T handler = future.getMessageHandler();
      try {
        if (handler != null) {
          try {
            handler.sessionTerminated();
          }
          catch (Throwable e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
          }
        }
      }
      finally {
        future.setDone();
      }
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    try {
      super.channelInactive(context);
    }
    finally {
      try {
        //invoke 'keySet()' method via 'Map' class because ConcurrentHashMap#keySet() has return type ('KeySetView') which doesn't exist in JDK 1.6/1.7
        Set<UUID> keys = ((Map<UUID, RequestFuture<T>>)myHandlers).keySet();

        for (UUID uuid : new ArrayList<>(keys)) {
          terminateSession(uuid);
        }
      }
      finally {
        // make sure the client is in disconnected state
        myAsyncExec.execute(() -> myClient.disconnect());
      }
    }
  }

  public void registerFuture(UUID messageId, RequestFuture<T> requestFuture) {
    myHandlers.put(messageId, requestFuture);
  }

  public RequestFuture<T> removeFuture(UUID messageId) {
    return myHandlers.remove(messageId);
  }
}
