package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class CompileContextImpl extends UserDataHolderBase implements CompileContext {
  private static final String CANCELED_MESSAGE = "The build has been canceled";
  private final CompileScope myScope;
  private final boolean myIsMake;
  private final boolean myIsProjectRebuild;
  private final ProjectChunks myProductionChunks;
  private final ProjectChunks myTestChunks;
  private final MessageHandler myDelegateMessageHandler;
  private volatile boolean myCompilingTests = false;
  private final Set<ModuleBuildTarget> myNonIncrementalModules = new HashSet<ModuleBuildTarget>();

  private final ProjectPaths myProjectPaths;
  private final long myCompilationStartStamp;
  private final ProjectDescriptor myProjectDescriptor;
  private final Map<String, String> myBuilderParams;
  private final CanceledStatus myCancelStatus;
  private volatile float myDone = -1.0f;
  private EventDispatcher<BuildListener> myListeners = EventDispatcher.create(BuildListener.class);
  private Map<JpsModule, AnnotationProcessingProfile> myAnnotationProcessingProfileMap;

  public CompileContextImpl(CompileScope scope,
                            ProjectDescriptor pd, boolean isMake,
                            boolean isProjectRebuild,
                            ProjectChunks productionChunks,
                            ProjectChunks testChunks,
                            MessageHandler delegateMessageHandler,
                            Map<String, String> builderParams,
                            CanceledStatus cancelStatus) throws ProjectBuildException {
    myProjectDescriptor = pd;
    myBuilderParams = Collections.unmodifiableMap(builderParams);
    myCancelStatus = cancelStatus;
    myCompilationStartStamp = System.currentTimeMillis();
    myScope = scope;
    myIsProjectRebuild = isProjectRebuild;
    myIsMake = !isProjectRebuild && isMake;
    myProductionChunks = productionChunks;
    myTestChunks = testChunks;
    myDelegateMessageHandler = delegateMessageHandler;
    myProjectPaths = new ProjectPaths(pd.jpsProject);
  }

  @Override
  public long getCompilationStartStamp() {
    return myCompilationStartStamp;
  }

  @Override
  public ProjectChunks getTestChunks() {
    return myTestChunks;
  }

  @Override
  public ProjectChunks getProductionChunks() {
    return myProductionChunks;
  }

  @Override
  public ProjectPaths getProjectPaths() {
    return myProjectPaths;
  }

  @Override
  public boolean isMake() {
    return myIsMake;
  }

  @Override
  public boolean isProjectRebuild() {
    return myIsProjectRebuild;
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
  @NotNull
  public AnnotationProcessingProfile getAnnotationProcessingProfile(JpsModule module) {
    final CompilerConfiguration compilerConfig = getProjectDescriptor().project.getCompilerConfiguration();
    Map<JpsModule, AnnotationProcessingProfile> map = myAnnotationProcessingProfileMap;
    if (map == null) {
      map = new HashMap<JpsModule, AnnotationProcessingProfile>();
      final Map<String, JpsModule> namesMap = new HashMap<String, JpsModule>();
      for (JpsModule m : getProjectDescriptor().jpsProject.getModules()) {
        namesMap.put(m.getName(), m);
      }
      if (!namesMap.isEmpty()) {
        for (AnnotationProcessingProfile profile : compilerConfig.getModuleAnnotationProcessingProfiles()) {
          for (String name : profile.getProcessModule()) {
            final JpsModule mod = namesMap.get(name);
            if (mod != null) {
              map.put(mod, profile);
            }
          }
        }
      }
      myAnnotationProcessingProfileMap = map;
    }
    final AnnotationProcessingProfile profile = map.get(module);
    return profile != null? profile : compilerConfig.getDefaultAnnotationProcessingProfile();
  }

  @Override
  public void markNonIncremental(RealModuleBuildTarget target) {
    if (!isCompilingTests()) {
      myNonIncrementalModules.add(new RealModuleBuildTarget(target.getModule(), JavaModuleBuildTargetType.PRODUCTION));
    }
    myNonIncrementalModules.add(new RealModuleBuildTarget(target.getModule(), JavaModuleBuildTargetType.TEST));
  }

  @Override
  public boolean shouldDifferentiate(ModuleChunk chunk) {
    if (!isMake()) {
      // the check makes sense only in make mode
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
  public boolean isCompilingTests() {
    return myCompilingTests;
  }

  @Override
  public final CanceledStatus getCancelStatus() {
    return myCancelStatus;
  }

  @Override
  public final void checkCanceled() throws ProjectBuildException {
    if (getCancelStatus().isCanceled()) {
      throw new ProjectBuildException(CANCELED_MESSAGE);
    }
  }

  void setCompilingTests(boolean compilingTests) {
    myCompilingTests = compilingTests;
  }

  @Override
  public void clearNonIncrementalMark(RealModuleBuildTarget target) {
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
