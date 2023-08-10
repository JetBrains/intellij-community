// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service;

import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ClassPathUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.wrapper.ExternalSystemFacadeWrapper;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.serialization.ObjectSerializer;
import com.intellij.ui.PlaceHolder;
import com.intellij.util.Alarm;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import kotlin.reflect.full.NoSuchPropertyException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.application.PathManager.getJarPathForClass;

@Service(Service.Level.APP)
public final class RemoteExternalSystemCommunicationManager implements ExternalSystemCommunicationManager, Disposable {
  private static final Logger LOG = Logger.getInstance(RemoteExternalSystemCommunicationManager.class);

  private static final String MAIN_CLASS_NAME = RemoteExternalSystemFacadeImpl.class.getName();

  private final AtomicReference<RemoteExternalSystemProgressNotificationManager> myExportedNotificationManager
    = new AtomicReference<>();

  @NotNull private final ThreadLocal<ProjectSystemId> myTargetExternalSystemId = new ThreadLocal<>();

  @NotNull private final ExternalSystemProgressNotificationManagerImpl                    myProgressManager;
  @NotNull private final RemoteProcessSupport<Object, RemoteExternalSystemFacade, String> mySupport;

  public RemoteExternalSystemCommunicationManager() {
    myProgressManager = (ExternalSystemProgressNotificationManagerImpl)ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager.class);
    mySupport = new RemoteProcessSupport<>(RemoteExternalSystemFacade.class) {
      @Override
      protected void fireModificationCountChanged() {
      }

      @Override
      protected String getName(@NotNull Object o) {
        return RemoteExternalSystemFacade.class.getName();
      }

      @Override
      protected RunProfileState getRunProfileState(@NotNull Object o, @NotNull String configuration, @NotNull Executor executor) {
        return createRunProfileState(configuration);
      }
    };

    ShutDownTracker.getInstance().registerShutdownTask(() -> shutdown(false));
  }

  public synchronized void shutdown(boolean wait) {
    mySupport.stopAll(wait);
  }

  private RunProfileState createRunProfileState(final String configuration) {
    return new RunProfileState() {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {

        final SimpleJavaParameters params = new SimpleJavaParameters();
        params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));

        File myWorkingDirectory = new File(configuration);
        params.setWorkingDirectory(myWorkingDirectory.isDirectory() ? myWorkingDirectory.getPath() : PathManager.getBinPath());

        // IDE jars.
        Collection<String> classPath = new LinkedHashSet<>(ClassPathUtil.getUtilClassPath());
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(Project.class)); //intellij.platform.core
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(PlaceHolder.class)); //intellij.platform.editor
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(DependencyScope.class)); //intellij.platform.projectModel
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(ProjectExtension.class)); //intellij.platform.projectModel.impl
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(Alarm.class)); //intellij.platform.ide
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(ExtensionPointName.class)); //intellij.platform.extensions
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(StorageUtilKt.class)); //intellij.platform.ide.impl
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(ExternalSystemTaskNotificationListener.class)); //intellij.platform.externalSystem
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(AtomicFieldUpdater.class)); //intellij.platform.concurrency

        // java plugin jar if it's installed
        Class<? extends SdkType> javaSdkClass = ExternalSystemJdkProvider.getInstance().getJavaSdkType().getClass();
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(javaSdkClass));

        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(ModuleType.class));
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(EmptyModuleType.class));

        // add Kotlin runtime
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(Unit.class));
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(NoSuchPropertyException.class));

        // External system module jars
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(getClass()));
        // external-system-rt.jar
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(ExternalSystemException.class));
        // com.intellij.openapi.externalSystem.model.FSTSerializer dependencies
        ContainerUtil.addIfNotNull(classPath, getJarPathForClass(ObjectSerializer.class));

        params.getClassPath().addAll(new ArrayList<>(classPath));

        params.setMainClass(MAIN_CLASS_NAME);
        params.getVMParametersList().addParametersString("-Djava.awt.headless=true");

        // It may take a while for external system api to resolve external dependencies. Default RMI timeout
        // is 15 seconds (http://download.oracle.com/javase/6/docs/technotes/guides/rmi/sunrmiproperties.html#connectionTimeout),
        // we don't want to get EOFException because of that.
        params.getVMParametersList().addParametersString(
          "-Dsun.rmi.transport.connectionTimeout=" + TimeUnit.HOURS.toMillis(1)
        );
        final String debugPort = System.getProperty(ExternalSystemConstants.EXTERNAL_SYSTEM_REMOTE_COMMUNICATION_MANAGER_DEBUG_PORT);
        if (debugPort != null) {
          params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=" + debugPort);
        }

        ProjectSystemId externalSystemId = myTargetExternalSystemId.get();
        if (externalSystemId != null) {
          ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
          if (manager != null) {
            params.getClassPath().add(getJarPathForClass(manager.getProjectResolverClass()));
            params.getClassPath().add(getJarPathForClass(manager.getClass().getSuperclass()));
            params.getProgramParametersList().add(manager.getProjectResolverClass().getName());
            params.getProgramParametersList().add(manager.getTaskManagerClass().getName());
            manager.enhanceRemoteProcessing(params);
          }
        }

        return params;
      }

      @Override
      @NotNull
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(processHandler);
      }

      @NotNull
      private OSProcessHandler startProcess() throws ExecutionException {
        SimpleJavaParameters params = createJavaParameters();
        GeneralCommandLine commandLine = params.toCommandLine();
        OSProcessHandler processHandler = new OSProcessHandler(commandLine);
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }
    };
  }

  @Nullable
  @Override
  public RemoteExternalSystemFacade acquire(@NotNull String id, @NotNull ProjectSystemId externalSystemId)
    throws Exception
  {
    myTargetExternalSystemId.set(externalSystemId);
    final RemoteExternalSystemFacade facade;
    try {
      facade = mySupport.acquire(this, id);
    }
    finally {
      myTargetExternalSystemId.set(null);
    }
    if (facade == null) {
      return null;
    }

    RemoteExternalSystemProgressNotificationManager exported = myExportedNotificationManager.get();
    if (exported == null) {
      try {
        exported = (RemoteExternalSystemProgressNotificationManager)UnicastRemoteObject.exportObject(myProgressManager, 0);
        myExportedNotificationManager.set(exported);
      }
      catch (RemoteException e) {
        exported = myExportedNotificationManager.get();
      }
    }
    if (exported == null) {
      LOG.warn("Can't export progress manager");
    }
    else {
      facade.applyProgressManager(exported);
    }
    return wrapResolverDeserialization(facade);
  }

  @NotNull
  private static RemoteExternalSystemFacade wrapResolverDeserialization(@NotNull RemoteExternalSystemFacade facade) {
    return new ResolverDeserializationWrapper(facade);
  }


  @Override
  public void release(@NotNull String id, @NotNull ProjectSystemId externalSystemId) {
    mySupport.release(this, id);
  }

  @Override
  public boolean isAlive(@NotNull RemoteExternalSystemFacade facade) {
    RemoteExternalSystemFacade toCheck = facade;
    if (facade instanceof ExternalSystemFacadeWrapper) {
      toCheck = ((ExternalSystemFacadeWrapper)facade).getDelegate();

    }
    if (toCheck instanceof InProcessExternalSystemFacadeImpl) {
      return false;
    }
    try {
      facade.getResolver();
      return true;
    }
    catch (RemoteException e) {
      return false;
    }
  }

  @Override
  public void clear() {
    mySupport.stopAll(true);
  }

  @Override
  public void dispose() {
    shutdown(false);
  }
}
