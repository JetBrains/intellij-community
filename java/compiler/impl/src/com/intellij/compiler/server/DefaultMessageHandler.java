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

import org.jetbrains.jps.api.CmdlineRemoteProto;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/18/12
 */
public abstract class DefaultMessageHandler implements BuilderMessageHandler {
  @Override
  public final void handleBuildMessage(CmdlineRemoteProto.Message.BuilderMessage msg) {
    switch (msg.getType()) {
      case BUILD_EVENT:
        handleBuildEvent(msg.getBuildEvent());
        break;
      case COMPILE_MESSAGE:
        handleCompileMessage(msg.getCompileMessage());
        break;
      case FS_STATE:
        handleFSStateMessage(msg.getFsstateMessage());
        break;
    }
  }

  protected void handleFSStateMessage(CmdlineRemoteProto.Message.FSStateMessage message) {
  }

  protected abstract void handleCompileMessage(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message);

  protected abstract void handleBuildEvent(CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event);

}
