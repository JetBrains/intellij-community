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
package com.intellij.compiler.server;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/18/12
 */
public abstract class DefaultMessageHandler implements BuilderMessageHandler {
  @Override
  public final void handleBuildMessage(Channel channel, UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage msg) {
    switch (msg.getType()) {
      case BUILD_EVENT:
        handleBuildEvent(msg.getBuildEvent());
        break;
      case COMPILE_MESSAGE:
        handleCompileMessage(msg.getCompileMessage());
        break;
      case CONSTANT_SEARCH_TASK:
        handleConstantSearchTask(channel, sessionId, msg.getConstantSearchTask());
    }
  }

  protected void handleConstantSearchTask(Channel channel, UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.ConstantSearchTask task) {
    // todo: complete impl
    final CmdlineRemoteProto.Message.ControllerMessage.ConstantSearchResult.Builder builder =
      CmdlineRemoteProto.Message.ControllerMessage.ConstantSearchResult.newBuilder();
    builder.setOwnerClassName(task.getOwnerClassName());
    builder.setFieldName(task.getFieldName());
    builder.setIsSuccess(false); // todo
    Channels.write(channel, CmdlineProtoUtil.toMessage(sessionId, CmdlineRemoteProto.Message.ControllerMessage.newBuilder().setType(
      CmdlineRemoteProto.Message.ControllerMessage.Type.CONSTANT_SEARCH_RESULT).setConstantSearchResult(builder.build()).build()
    ));
  }

  protected abstract void handleCompileMessage(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message);

  protected abstract void handleBuildEvent(CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event);

}
