// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.DynamicBundle;
import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.plugins.DisabledPluginsState;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockFileDocumentManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.CoreCommandProcessor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.ClassExtension;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileManagerListener;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.impl.CoreVirtualFilePointerManager;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.PsiReferenceServiceImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistryImpl;
import com.intellij.psi.stubs.CoreStubTreeLoader;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.util.Consumer;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.Processor;
import com.intellij.util.graph.GraphAlgorithms;
import com.intellij.util.graph.impl.GraphAlgorithmsImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author yole
 */
public class CoreApplicationEnvironment {
  private final CoreFileTypeRegistry myFileTypeRegistry;
  protected final MockApplication myApplication;
  private final CoreLocalFileSystem myLocalFileSystem;
  @NotNull
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

    DisabledPluginsState.setIgnoreDisabledPlugins(true);

    myFileTypeRegistry = new CoreFileTypeRegistry();

    myApplication = createApplication(myParentDisposable);
    ApplicationManager.setApplication(myApplication,
                                      () -> myFileTypeRegistry,
                                      myParentDisposable);
    myLocalFileSystem = createLocalFileSystem();
    myJarFileSystem = createJarFileSystem();
    myJrtFileSystem = createJrtFileSystem();

    registerApplicationService(FileDocumentManager.class, new MockFileDocumentManagerImpl(null, DocumentImpl::new));

    registerApplicationExtensionPoint(new ExtensionPointName<>("com.intellij.virtualFileManagerListener"), VirtualFileManagerListener.class);
    List<VirtualFileSystem> fs = myJrtFileSystem != null
                             ? Arrays.asList(myLocalFileSystem, myJarFileSystem, myJrtFileSystem)
                             : Arrays.asList(myLocalFileSystem, myJarFileSystem);
    registerApplicationService(VirtualFileManager.class, new VirtualFileManagerImpl(fs));

    //fake EP for cleaning resources after area disposing (otherwise KeyedExtensionCollector listener will be copied to the next area)
    registerApplicationExtensionPoint(new ExtensionPointName<>("com.intellij.virtualFileSystem"), KeyedLazyInstanceEP.class);

    registerApplicationService(EncodingManager.class, new CoreEncodingRegistry());
    registerApplicationService(VirtualFilePointerManager.class, createVirtualFilePointerManager());
    registerApplicationService(DefaultASTFactory.class, new DefaultASTFactoryImpl());
    registerApplicationService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
    registerApplicationService(ReferenceProvidersRegistry.class, new ReferenceProvidersRegistryImpl());
    registerApplicationService(StubTreeLoader.class, new CoreStubTreeLoader());
    registerApplicationService(PsiReferenceService.class, new PsiReferenceServiceImpl());
    registerApplicationService(ProgressManager.class, createProgressIndicatorProvider());
    registerApplicationService(JobLauncher.class, createJobLauncher());
    registerApplicationService(CodeFoldingSettings.class, new CodeFoldingSettings());
    registerApplicationService(CommandProcessor.class, new CoreCommandProcessor());
    registerApplicationService(GraphAlgorithms.class, new GraphAlgorithmsImpl());

    myApplication.registerService(ApplicationInfo.class, ApplicationInfoImpl.class);

    registerApplicationExtensionPoint(DynamicBundle.LanguageBundleEP.EP_NAME, DynamicBundle.LanguageBundleEP.class);
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
    return new MockApplication(parentDisposable) {
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
      public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
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
      public Job<Void> submitToJobThread(@NotNull Runnable action, Consumer<? super Future<?>> onDoneCallback) {
        action.run();
        if (onDoneCallback != null) {
          onDoneCallback.consume(CompletableFuture.completedFuture(null));
        }
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
    if (implementation instanceof Disposable) {
      Disposer.register(myApplication, (Disposable)implementation);
    }
  }

  public void registerFileType(@NotNull FileType fileType, @NotNull @NonNls String extension) {
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
    instance.addExplicitExtension(language, object, myParentDisposable);
  }

  public void registerParserDefinition(@NotNull Language language, @NotNull ParserDefinition parserDefinition) {
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, language, parserDefinition);
  }

  public <T> void addExplicitExtension(@NotNull final FileTypeExtension<T> instance, @NotNull final FileType fileType, @NotNull final T object) {
    instance.addExplicitExtension(fileType, object, myParentDisposable);
  }

  public <T> void addExplicitExtension(@NotNull final ClassExtension<T> instance, @NotNull final Class aClass, @NotNull final T object) {
    instance.addExplicitExtension(aClass, object, myParentDisposable);
  }

  public <T> void addExtension(@NotNull ExtensionPointName<T> name, @NotNull final T extension) {
    final ExtensionPoint<T> extensionPoint = Extensions.getRootArea().getExtensionPoint(name);
    //noinspection TestOnlyProblems
    extensionPoint.registerExtension(extension, myParentDisposable);
  }

  public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area,
                                                @NotNull ExtensionPointName<T> extensionPointName,
                                                @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(area, extensionPointName.getName(), aClass);
  }

  public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area,
                                                @NotNull BaseExtensionPointName extensionPointName,
                                                @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(area, extensionPointName.getName(), aClass);
  }

  public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area, @NotNull String name, @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(area, name, aClass, false);
  }

  public static <T> void registerDynamicExtensionPoint(@NotNull ExtensionsArea area, @NotNull String name, @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(area, name, aClass, true);
  }

  @SuppressWarnings("TestOnlyProblems")
  private static <T> void registerExtensionPoint(@NotNull ExtensionsArea area, @NotNull String name, @NotNull Class<? extends T> aClass, boolean dymanic) {
    if (!area.hasExtensionPoint(name)) {
      ExtensionPoint.Kind kind = aClass.isInterface() || Modifier.isAbstract(aClass.getModifiers()) ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
      if (dymanic) {
        area.registerDynamicExtensionPoint(name, aClass.getName(), kind);
      }
      else {
        area.registerExtensionPoint(name, aClass.getName(), kind);
      }
    }
  }

  @SuppressWarnings("deprecation")
  public static <T> void registerApplicationExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(Extensions.getRootArea(), extensionPointName, aClass);
  }

  @SuppressWarnings("deprecation")
  public static <T> void registerApplicationDynamicExtensionPoint(@NotNull String extensionPointName, @NotNull Class<? extends T> aClass) {
    registerDynamicExtensionPoint(Extensions.getRootArea(), extensionPointName, aClass);
  }

  public static void registerExtensionPointAndExtensions(@NotNull Path pluginRoot, @NotNull String fileName, @NotNull ExtensionsArea area) {
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
