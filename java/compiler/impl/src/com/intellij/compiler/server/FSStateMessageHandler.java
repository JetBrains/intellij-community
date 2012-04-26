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
import org.jetbrains.jps.incremental.fs.FSState;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/18/12
 */
public class FSStateMessageHandler extends DefaultMessageHandler {
  private final FSState myFsState;
  private final DefaultMessageHandler myDelegate;

  protected FSStateMessageHandler(FSState fsState, DefaultMessageHandler delegate) {
    myFsState = fsState;
    myDelegate = delegate;
  }

  @Override
  public void handleFailure(CmdlineRemoteProto.Message.Failure failure) {
    myDelegate.handleFailure(failure);
  }

  @Override
  public void sessionTerminated() {
    myDelegate.sessionTerminated();
  }

  protected void handleFSStateMessage(CmdlineRemoteProto.Message.FSStateMessage message) {
    try {
      myDelegate.handleFSStateMessage(message);
    }
    finally {
      final String moduleName = message.getModuleName();
      final List<String> deleteProduction = message.getDeletedProductionList();
      final List<String> deletedTests = message.getDeletedTestsList();

      final Map<File,Set<File>> recompileProduction = new HashMap<File, Set<File>>();
      final Map<File, Set<File>> recompileTests = new HashMap<File, Set<File>>();
      for (CmdlineRemoteProto.Message.FSStateMessage.RootDelta delta : message.getRecompileDeltaList()) {
        final File root = new File(delta.getRoot());
        final Map<File, Set<File>> map = delta.getTestSources()? recompileTests : recompileProduction;
        Set<File> files = map.get(root);
        if (files == null) {
          files = new HashSet<File>();
          map.put(root, files);
        }
        for (String path : delta.getPathList()) {
          files.add(new File(path));
        }
      }

      myFsState.init(moduleName, deleteProduction, deletedTests, recompileProduction, recompileTests);
    }
  }

  protected void handleCompileMessage(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message) {
    myDelegate.handleCompileMessage(message);
  }

  protected void handleBuildEvent(CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event) {
    myDelegate.handleBuildEvent(event);
  }

}
