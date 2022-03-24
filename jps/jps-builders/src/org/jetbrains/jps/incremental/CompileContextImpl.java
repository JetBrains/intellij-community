// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.EventDispatcher;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.JpsBuildBundle;
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
 */
public class CompileContextImpl extends UserDataHolderBase implements CompileContext {
  private final CompileScope myScope;
  private final MessageHandler myDelegateMessageHandler;
  private final Set<ModuleBuildTarget> myNonIncrementalModules = new HashSet<>();

  private final Object2LongMap<BuildTarget<?>> myCompilationStartStamp = new Object2LongOpenHashMap<>();
  private final ProjectDescriptor myProjectDescriptor;
  private final Map<String, String> myBuilderParams;
  private final CanceledStatus myCancelStatus;
  private volatile float myDone = -1.0f;
  private final EventDispatcher<BuildListener> myListeners = EventDispatcher.create(BuildListener.class);

  CompileContextImpl(CompileScope scope,
                     ProjectDescriptor pd,
                     MessageHandler delegateMessageHandler,
                     Map<String, String> builderParams,
                     CanceledStatus cancelStatus) {
    myProjectDescriptor = pd;
    myBuilderParams = Collections.unmodifiableMap(builderParams);
    myCancelStatus = cancelStatus;
    myScope = scope;
    myDelegateMessageHandler = delegateMessageHandler;
  }

  @TestOnly
  public static CompileContext createContextForTests(CompileScope scope, ProjectDescriptor descriptor) {
    return new CompileContextImpl(scope, descriptor, DEAF, Collections.emptyMap(), CanceledStatus.NULL);
  }

  @Override
  public long getCompilationStartStamp(BuildTarget<?> target) {
    synchronized (myCompilationStartStamp) {
      return myCompilationStartStamp.getLong(target);
    }
  }

  @Override
  public void setCompilationStartStamp(Collection<? extends BuildTarget<?>> targets, long stamp) {
    synchronized (myCompilationStartStamp) {
      for (BuildTarget<?> target : targets) {
        myCompilationStartStamp.put(target, stamp);
      }
    }
  }

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
      throw new StopBuildException(JpsBuildBundle.message("build.message.the.build.has.been.canceled"));
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

  @Override
  public void processMessage(BuildMessage msg) {
    if (msg.getKind() == BuildMessage.Kind.ERROR) {
      Utils.ERRORS_DETECTED_KEY.set(this, Boolean.TRUE);
    }
    if (msg instanceof ProgressMessage) {
      ((ProgressMessage)msg).setDone(myDone);
    }
    myDelegateMessageHandler.processMessage(msg);
    if (msg instanceof FileGeneratedEvent) {
      myListeners.getMulticaster().filesGenerated((FileGeneratedEvent)msg);
    }
    else if (msg instanceof FileDeletedEvent) {
      myListeners.getMulticaster().filesDeleted((FileDeletedEvent)msg);
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
