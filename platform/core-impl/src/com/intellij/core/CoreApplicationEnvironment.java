// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.concurrency.AsyncFuture;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockApplicationEx;
import com.intellij.mock.MockFileDocumentManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.CoreCommandProcessor;
import com.intellij.openapi.components.ExtensionAreas;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.ClassExtension;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.util.StaticGetter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.impl.CoreVirtualFilePointerManager;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.PsiReferenceServiceImpl;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistryImpl;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.stubs.CoreStubTreeLoader;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.graph.GraphAlgorithms;
import com.intellij.util.graph.impl.GraphAlgorithmsImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author yole
 */
public class CoreApplicationEnvironment {
  private final CoreFileTypeRegistry myFileTypeRegistry;
  protected final MockApplication myApplication;
  private final CoreLocalFileSystem myLocalFileSystem;
  protected final VirtualFileSystem myJarFileSystem;
  private final VirtualFileSystem myJrtFileSystem;
  @NotNull private final Disposable myParentDisposable;
  private final boolean myUnitTestMode;

  public CoreApplicationEnvironment(@NotNull Disposable parentDisposable) {
    this(parentDisposable, true);
  }

  public CoreApplicationEnvironment(@NotNull Disposable parentDisposable, boolean unitTestMode) {
    myParentDisposable = parentDisposable;
    myUnitTestMode = unitTestMode;

    myFileTypeRegistry = new CoreFileTypeRegistry();

    myApplication = createApplication(myParentDisposable);
    ApplicationManager.setApplication(myApplication,
                                      new StaticGetter<>(myFileTypeRegistry),
                                      myParentDisposable);
    myLocalFileSystem = createLocalFileSystem();
    myJarFileSystem = createJarFileSystem();
    myJrtFileSystem = createJrtFileSystem();

    Extensions.registerAreaClass(ExtensionAreas.IDEA_PROJECT, null);

    final MutablePicoContainer appContainer = myApplication.getPicoContainer();
    registerComponentInstance(appContainer, FileDocumentManager.class, new MockFileDocumentManagerImpl(
      charSequence -> new DocumentImpl(charSequence), null));

    VirtualFileSystem[] fs = myJrtFileSystem != null
                             ? new VirtualFileSystem[]{myLocalFileSystem, myJarFileSystem, myJrtFileSystem}
                             : new VirtualFileSystem[]{myLocalFileSystem, myJarFileSystem};
    VirtualFileManagerImpl virtualFileManager = new VirtualFileManagerImpl(fs, myApplication.getMessageBus());
    registerComponentInstance(appContainer, VirtualFileManager.class, virtualFileManager);

    registerApplicationService(EncodingManager.class, new CoreEncodingRegistry());
    registerApplicationService(VirtualFilePointerManager.class, createVirtualFilePointerManager());
    registerApplicationService(DefaultASTFactory.class, new CoreASTFactory());
    registerApplicationService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
    registerApplicationService(ReferenceProvidersRegistry.class, new ReferenceProvidersRegistryImpl());
    registerApplicationService(StubTreeLoader.class, new CoreStubTreeLoader());
    registerApplicationService(PsiReferenceService.class, new PsiReferenceServiceImpl());
    registerApplicationService(MetaDataRegistrar.class, new MetaRegistry());
    registerApplicationService(ProgressManager.class, createProgressIndicatorProvider());
    registerApplicationService(JobLauncher.class, createJobLauncher());
    registerApplicationService(CodeFoldingSettings.class, new CodeFoldingSettings());
    registerApplicationService(CommandProcessor.class, new CoreCommandProcessor());
    registerApplicationService(GraphAlgorithms.class, new GraphAlgorithmsImpl());

    myApplication.registerService(ApplicationInfo.class, ApplicationInfoImpl.class);
  }

  public <T> void registerApplicationService(@NotNull Class<T> serviceInterface, @NotNull T serviceImplementation) {
    myApplication.registerService(serviceInterface, serviceImplementation);
  }

  @NotNull
  protected VirtualFilePointerManager createVirtualFilePointerManager() {
    return new CoreVirtualFilePointerManager();
  }

  @NotNull
  protected MockApplication createApplication(@NotNull Disposable parentDisposable) {
    return new MockApplicationEx(parentDisposable) {
      @Override
      public boolean isUnitTestMode() {
        return myUnitTestMode;
      }
    };
  }

  @NotNull
  protected JobLauncher createJobLauncher() {
    return new JobLauncher() {
      @Override
      public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<T> things,
                                                         ProgressIndicator progress,
                                                         boolean runInReadAction,
                                                         boolean failFastOnAcquireReadAction,
                                                         @NotNull Processor<? super T> thingProcessor) {
        for (T thing : things) {
          if (!thingProcessor.process(thing))
            return false;
        }
        return true;
      }

      @NotNull
      @Override
      public <T> AsyncFuture<Boolean> invokeConcurrentlyUnderProgressAsync(@NotNull List<T> things,
                                                                           ProgressIndicator progress,
                                                                           boolean failFastOnAcquireReadAction,
                                                                           @NotNull Processor<? super T> thingProcessor) {
        return AsyncUtil.wrapBoolean(invokeConcurrentlyUnderProgress(things, progress, failFastOnAcquireReadAction, thingProcessor));
      }

      @NotNull
      @Override
      public Job<Void> submitToJobThread(@NotNull Runnable action, Consumer<Future> onDoneCallback) {
        action.run();
        if (onDoneCallback != null)
          onDoneCallback.consume(new Future() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
              return false;
            }

            @Override
            public boolean isCancelled() {
              return false;
            }

            @Override
            public boolean isDone() {
              return true;
            }

            @Override
            public Object get() {
              return null;
            }

            @Override
            public Object get(long timeout, @NotNull TimeUnit unit) {
              return null;
            }
          });
        return Job.NULL_JOB;
      }
    };
  }

  @NotNull
  protected ProgressManager createProgressIndicatorProvider() {
    return new CoreProgressManager();
  }

  @NotNull
  protected VirtualFileSystem createJarFileSystem() {
    return new CoreJarFileSystem();
  }

  @NotNull
  protected CoreLocalFileSystem createLocalFileSystem() {
    return new CoreLocalFileSystem();
  }

  @Nullable
  protected VirtualFileSystem createJrtFileSystem() {
    return null;
  }

  @NotNull
  public MockApplication getApplication() {
    return myApplication;
  }

  @NotNull
  public Disposable getParentDisposable() {
    return myParentDisposable;
  }

  public <T> void registerApplicationComponent(@NotNull Class<T> interfaceClass, @NotNull T implementation) {
    registerComponentInstance(myApplication.getPicoContainer(), interfaceClass, implementation);
  }

  public void registerFileType(@NotNull FileType fileType, @NotNull String extension) {
    myFileTypeRegistry.registerFileType(fileType, extension);
  }

  public void registerParserDefinition(@NotNull ParserDefinition definition) {
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, definition.getFileNodeType().getLanguage(), definition);
  }

  public static <T> void registerComponentInstance(@NotNull MutablePicoContainer container, @NotNull Class<T> key, @NotNull T implementation) {
    container.unregisterComponent(key);
    container.registerComponentInstance(key, implementation);
  }

  public <T> void addExplicitExtension(@NotNull LanguageExtension<T> instance, @NotNull Language language, @NotNull T object) {
    doAddExplicitExtension(instance, language, object);
  }

  public void registerParserDefinition(@NotNull Language language, @NotNull ParserDefinition parserDefinition) {
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, language, parserDefinition);
  }

  public <T> void addExplicitExtension(@NotNull final FileTypeExtension<T> instance, @NotNull final FileType fileType, @NotNull final T object) {
    doAddExplicitExtension(instance, fileType, object);
  }

  private <T,U> void doAddExplicitExtension(@NotNull final KeyedExtensionCollector<T,U> instance, @NotNull final U key, @NotNull final T object) {
    instance.addExplicitExtension(key, object);
    Disposer.register(myParentDisposable, new Disposable() {
      @Override
      public void dispose() {
        instance.removeExplicitExtension(key, object);
      }
    });
  }

  public <T> void addExplicitExtension(@NotNull final ClassExtension<T> instance, @NotNull final Class aClass, @NotNull final T object) {
    doAddExplicitExtension(instance, aClass, object);
  }

  public <T> void addExtension(@NotNull ExtensionPointName<T> name, @NotNull final T extension) {
    final ExtensionPoint<T> extensionPoint = Extensions.getRootArea().getExtensionPoint(name);
    extensionPoint.registerExtension(extension);
    Disposer.register(myParentDisposable, new Disposable() {
      @Override
      public void dispose() {
        // There is a possible case that particular extension was replaced in particular environment, e.g. Upsource
        // replaces some IntelliJ extensions.
        if (extensionPoint.hasExtension(extension)) {
          extensionPoint.unregisterExtension(extension);
        }
      }
    });
  }


  public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area,
                                                @NotNull ExtensionPointName<T> extensionPointName,
                                                @NotNull Class<? extends T> aClass) {
    final String name = extensionPointName.getName();
    registerExtensionPoint(area, name, aClass);
  }

  public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area, @NotNull String name, @NotNull Class<? extends T> aClass) {
    if (!area.hasExtensionPoint(name)) {
      ExtensionPoint.Kind kind = aClass.isInterface() || Modifier.isAbstract(aClass.getModifiers()) ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
      area.registerExtensionPoint(name, aClass.getName(), kind);
    }
  }

  public static <T> void registerApplicationExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(Extensions.getRootArea(), extensionPointName, aClass);
  }

  public static void registerExtensionPointAndExtensions(@NotNull File pluginRoot, @NotNull String fileName, @NotNull ExtensionsArea area) {
    PluginManagerCore.registerExtensionPointAndExtensions(pluginRoot, fileName, area);
  }

  @NotNull
  public CoreLocalFileSystem getLocalFileSystem() {
    return myLocalFileSystem;
  }

  @NotNull
  public VirtualFileSystem getJarFileSystem() {
    return myJarFileSystem;
  }

  @Nullable
  public VirtualFileSystem getJrtFileSystem() {
    return myJrtFileSystem;
  }
}
