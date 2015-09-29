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
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.EventDispatcher;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class CompileContextImpl extends UserDataHolderBase implements CompileContext {
  private static final String CANCELED_MESSAGE = "The build has been canceled";
  private final CompileScope myScope;
  private final MessageHandler myDelegateMessageHandler;
  private final Set<ModuleBuildTarget> myNonIncrementalModules = new HashSet<ModuleBuildTarget>();

  private final TObjectLongHashMap<BuildTarget<?>> myCompilationStartStamp = new TObjectLongHashMap<BuildTarget<?>>();
  private final ProjectDescriptor myProjectDescriptor;
  private final Map<String, String> myBuilderParams;
  private final CanceledStatus myCancelStatus;
  private volatile float myDone = -1.0f;
  private EventDispatcher<BuildListener> myListeners = EventDispatcher.create(BuildListener.class);

  public CompileContextImpl(CompileScope scope,
                            ProjectDescriptor pd,
                            MessageHandler delegateMessageHandler,
                            Map<String, String> builderParams,
                            CanceledStatus cancelStatus) throws ProjectBuildException {
    myProjectDescriptor = pd;
    myBuilderParams = Collections.unmodifiableMap(builderParams);
    myCancelStatus = cancelStatus;
    myScope = scope;
    myDelegateMessageHandler = delegateMessageHandler;
  }

  @Override
  public long getCompilationStartStamp(BuildTarget<?> target) {
    synchronized (myCompilationStartStamp) {
      return myCompilationStartStamp.get(target);
    }
  }

  public void setCompilationStartStamp(Collection<BuildTarget<?>> targets, long stamp) {
    synchronized (myCompilationStartStamp) {
      for (BuildTarget<?> target : targets) {
        myCompilationStartStamp.put(target, stamp);
      }
    }
  }

  @Override
  public boolean isMake() {
    return JavaBuilderUtil.isCompileJavaIncrementally(this);
  }

  @Override
  public boolean isProjectRebuild() {
    return JavaBuilderUtil.isForcedRecompilationAllJavaModules(this);
  }

  @Override
  public BuildLoggingManager getLoggingManager() {
    return myProjectDescriptor.getLoggingManager();
  }

  @Override
  @Nullable
  public String getBuilderParameter(String paramName) {
    return myBuilderParams.get(paramName);
  }

  @Override
  public void addBuildListener(BuildListener listener) {
    myListeners.addListener(listener);
  }

  @Override
  public void removeBuildListener(BuildListener listener) {
    myListeners.removeListener(listener);
  }

  @Override
  public void markNonIncremental(ModuleBuildTarget target) {
    if (!target.isTests()) {
      myNonIncrementalModules.add(new ModuleBuildTarget(target.getModule(), JavaModuleBuildTargetType.TEST));
    }
    myNonIncrementalModules.add(target);
  }

  @Override
  public boolean shouldDifferentiate(ModuleChunk chunk) {
    if (myNonIncrementalModules.isEmpty()) {
      return true;
    }
    for (ModuleBuildTarget target : chunk.getTargets()) {
      if (myNonIncrementalModules.contains(target)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public final CanceledStatus getCancelStatus() {
    return myCancelStatus;
  }

  @Override
  public final void checkCanceled() throws ProjectBuildException {
    if (getCancelStatus().isCanceled()) {
      throw new StopBuildException(CANCELED_MESSAGE);
    }
  }

  @Override
  public void clearNonIncrementalMark(ModuleBuildTarget target) {
    myNonIncrementalModules.remove(target);
  }

  @Override
  public CompileScope getScope() {
    return myScope;
  }

  public void processMessage(BuildMessage msg) {
    if (msg.getKind() == BuildMessage.Kind.ERROR) {
      Utils.ERRORS_DETECTED_KEY.set(this, Boolean.TRUE);
    }
    if (msg instanceof ProgressMessage) {
      ((ProgressMessage)msg).setDone(myDone);
    }
    myDelegateMessageHandler.processMessage(msg);
    if (msg instanceof FileGeneratedEvent) {
      final Collection<Pair<String, String>> paths = ((FileGeneratedEvent)msg).getPaths();
      if (!paths.isEmpty()) {
        myListeners.getMulticaster().filesGenerated(paths);
      }
    }
    else if (msg instanceof FileDeletedEvent) {
      Collection<String> paths = ((FileDeletedEvent)msg).getFilePaths();
      myListeners.getMulticaster().filesDeleted(paths);
    }
  }

  @Override
  public void setDone(float done) {
    myDone = done;
  }

  @Override
  public ProjectDescriptor getProjectDescriptor() {
    return myProjectDescriptor;
  }
}
