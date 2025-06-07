// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.EventDispatcher;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
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

@ApiStatus.Internal
public final class CompileContextImpl extends UserDataHolderBase implements CompileContext {
  private final CompileScope scope;
  private final MessageHandler delegateMessageHandler;
  private final Set<ModuleBuildTarget> nonIncrementalModules = new HashSet<>();

  private final Object2LongMap<BuildTarget<?>> compilationStartStamp = new Object2LongOpenHashMap<>();
  private final ProjectDescriptor projectDescriptor;
  private final Map<String, String> builderParams;
  private final CanceledStatus cancelStatus;
  private volatile float done = -1.0f;
  private final EventDispatcher<BuildListener> listeners = EventDispatcher.create(BuildListener.class);

  public CompileContextImpl(@NotNull CompileScope scope,
                            @NotNull ProjectDescriptor projectDescriptor,
                            @NotNull MessageHandler delegateMessageHandler,
                            @NotNull Map<String, String> builderParams,
                            @NotNull CanceledStatus cancelStatus) {
    this.projectDescriptor = projectDescriptor;
    this.builderParams = Collections.unmodifiableMap(builderParams);
    this.cancelStatus = cancelStatus;
    this.scope = scope;
    this.delegateMessageHandler = delegateMessageHandler;
  }

  @TestOnly
  public static CompileContext createContextForTests(@NotNull CompileScope scope, @NotNull ProjectDescriptor descriptor) {
    return new CompileContextImpl(scope, descriptor, DEAF, Collections.emptyMap(), CanceledStatus.NULL);
  }

  @Override
  public long getCompilationStartStamp(BuildTarget<?> target) {
    synchronized (compilationStartStamp) {
      return compilationStartStamp.getLong(target);
    }
  }

  @Override
  public void setCompilationStartStamp(@NotNull Collection<? extends BuildTarget<?>> targets, long stamp) {
    synchronized (compilationStartStamp) {
      for (BuildTarget<?> target : targets) {
        compilationStartStamp.put(target, stamp);
      }
    }
  }

  public boolean isMake() {
    return JavaBuilderUtil.isCompileJavaIncrementally(this);
  }

  @Override
  public @NotNull BuildLoggingManager getLoggingManager() {
    return projectDescriptor.getLoggingManager();
  }

  @Override
  public @Nullable String getBuilderParameter(String paramName) {
    return builderParams.get(paramName);
  }

  @Override
  public void addBuildListener(@NotNull BuildListener listener) {
    listeners.addListener(listener);
  }

  @Override
  public void removeBuildListener(@NotNull BuildListener listener) {
    listeners.removeListener(listener);
  }

  @Override
  public void markNonIncremental(@NotNull ModuleBuildTarget target) {
    if (!target.isTests()) {
      nonIncrementalModules.add(new ModuleBuildTarget(target.getModule(), JavaModuleBuildTargetType.TEST));
    }
    nonIncrementalModules.add(target);
  }

  @Override
  public boolean shouldDifferentiate(@NotNull ModuleChunk chunk) {
    if (nonIncrementalModules.isEmpty()) {
      return true;
    }
    for (ModuleBuildTarget target : chunk.getTargets()) {
      if (nonIncrementalModules.contains(target)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public @NotNull CanceledStatus getCancelStatus() {
    return cancelStatus;
  }

  @Override
  public void checkCanceled() throws ProjectBuildException {
    if (getCancelStatus().isCanceled()) {
      throw new StopBuildException(JpsBuildBundle.message("build.message.the.build.has.been.canceled"));
    }
  }

  @Override
  public void clearNonIncrementalMark(@NotNull ModuleBuildTarget target) {
    nonIncrementalModules.remove(target);
  }

  @Override
  public @NotNull CompileScope getScope() {
    return scope;
  }

  @Override
  public void processMessage(BuildMessage message) {
    if (message.getKind() == BuildMessage.Kind.ERROR) {
      Utils.ERRORS_DETECTED_KEY.set(this, Boolean.TRUE);
    }
    if (message instanceof ProgressMessage) {
      ((ProgressMessage)message).setDone(done);
    }
    delegateMessageHandler.processMessage(message);
    if (message instanceof FileGeneratedEvent) {
      listeners.getMulticaster().filesGenerated((FileGeneratedEvent)message);
    }
    else if (message instanceof FileDeletedEvent) {
      listeners.getMulticaster().filesDeleted((FileDeletedEvent)message);
    }
  }

  @Override
  public void setDone(float done) {
    this.done = done;
  }

  @Override
  public @NotNull ProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }
}
