// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.DynamicBundle;
import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginDescriptorLoader;
import com.intellij.ide.plugins.PluginEnabler;
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
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtension;
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
import com.intellij.util.KeyedLazyInstanceEP;
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


public class CoreApplicationEnvironment {
  private final CoreFileTypeRegistry myFileTypeRegistry;
  protected final MockApplication application;
  private final CoreLocalFileSystem myLocalFileSystem;
  protected final @NotNull VirtualFileSystem myJarFileSystem;
  private final VirtualFileSystem myJrtFileSystem;
  private final @NotNull Disposable myParentDisposable;
  private final boolean myUnitTestMode;

  public CoreApplicationEnvironment(@NotNull Disposable parentDisposable) {
    this(parentDisposable, true);
  }

  public CoreApplicationEnvironment(@NotNull Disposable parentDisposable, boolean unitTestMode) {
    myParentDisposable = parentDisposable;
    myUnitTestMode = unitTestMode;

    PluginEnabler.HEADLESS.setIgnoredDisabledPlugins(true);

    myFileTypeRegistry = new CoreFileTypeRegistry();

    application = createApplication(myParentDisposable);
    ApplicationManager.setApplication(application,
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

    application.registerService(ApplicationInfo.class, ApplicationInfoImpl.class);

    registerApplicationExtensionPoint(DynamicBundle.LanguageBundleEP.EP_NAME, DynamicBundle.LanguageBundleEP.class);
  }

  public <T> void registerApplicationService(@NotNull Class<T> serviceInterface, @NotNull T serviceImplementation) {
    application.registerService(serviceInterface, serviceImplementation);
  }

  protected @NotNull VirtualFilePointerManager createVirtualFilePointerManager() {
    return new CoreVirtualFilePointerManager();
  }

  protected @NotNull MockApplication createApplication(@NotNull Disposable parentDisposable) {
    return new MockApplication(parentDisposable) {
      @Override
      public boolean isUnitTestMode() {
        return myUnitTestMode;
      }
    };
  }

  protected @NotNull JobLauncher createJobLauncher() {
    return new CoreJobLauncher();
  }

  protected @NotNull ProgressManager createProgressIndicatorProvider() {
    return new CoreProgressManager();
  }

  protected @NotNull VirtualFileSystem createJarFileSystem() {
    return new CoreJarFileSystem();
  }

  protected @NotNull CoreLocalFileSystem createLocalFileSystem() {
    return new CoreLocalFileSystem();
  }

  protected @Nullable VirtualFileSystem createJrtFileSystem() {
    return null;
  }

  public @NotNull MockApplication getApplication() {
    return application;
  }

  public @NotNull Disposable getParentDisposable() {
    return myParentDisposable;
  }

  public <T> void registerApplicationComponent(@NotNull Class<T> interfaceClass, @NotNull T implementation) {
    registerComponentInstance(application.getPicoContainer(), interfaceClass, implementation);
    if (implementation instanceof Disposable) {
      Disposer.register(application, (Disposable)implementation);
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

  public <T> void addExplicitExtension(final @NotNull FileTypeExtension<T> instance, final @NotNull FileType fileType, final @NotNull T object) {
    instance.addExplicitExtension(fileType, object, myParentDisposable);
  }

  public <T> void addExplicitExtension(final @NotNull ClassExtension<T> instance, final @NotNull Class aClass, final @NotNull T object) {
    instance.addExplicitExtension(aClass, object, myParentDisposable);
  }

  public <T> void addExtension(@NotNull ExtensionPointName<T> name, final @NotNull T extension) {
    final ExtensionPoint<T> extensionPoint = Extensions.getRootArea().getExtensionPoint(name);
    //noinspection TestOnlyProblems
    extensionPoint.registerExtension(extension, myParentDisposable);
  }

  public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area,
                                                @NotNull ExtensionPointName<T> extensionPointName,
                                                @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(area, extensionPointName.getName(), aClass);
  }

  public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area, @NotNull String name, @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(area, name, aClass, false);
  }

  private static <T> void registerExtensionPoint(@NotNull ExtensionsArea area,
                                                 @NotNull String name,
                                                 @NotNull Class<? extends T> aClass,
                                                 boolean isDynamic) {
    if (!area.hasExtensionPoint(name)) {
      ExtensionPoint.Kind kind = aClass.isInterface() || Modifier.isAbstract(aClass.getModifiers()) ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
      area.registerExtensionPoint(name, aClass.getName(), kind, isDynamic);
    }
  }

  public static <T> void registerApplicationExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(Extensions.getRootArea(), extensionPointName.getName(), aClass);
  }

  public static <T> void registerApplicationDynamicExtensionPoint(@NotNull String extensionPointName, @NotNull Class<? extends T> aClass) {
    registerExtensionPoint(Extensions.getRootArea(), extensionPointName, aClass, true);
  }

  public static void registerExtensionPointAndExtensions(@NotNull Path pluginRoot, @NotNull String fileName, @NotNull ExtensionsArea area) {
    IdeaPluginDescriptorImpl descriptor = PluginDescriptorLoader.loadForCoreEnv(pluginRoot, fileName);
    if (descriptor == null) {
      PluginManagerCore.getLogger().error("Cannot load " + fileName + " from " + pluginRoot);
      return;
    }

    List<ExtensionPointDescriptor> extensionPoints = descriptor.appContainerDescriptor.extensionPoints;
    ExtensionsAreaImpl areaImpl = (ExtensionsAreaImpl)area;
    if (extensionPoints != null) {
      areaImpl.registerExtensionPoints(extensionPoints, descriptor);
    }
    descriptor.registerExtensions(areaImpl.extensionPoints, descriptor.appContainerDescriptor, null);
  }

  public @NotNull CoreLocalFileSystem getLocalFileSystem() {
    return myLocalFileSystem;
  }

  public @NotNull VirtualFileSystem getJarFileSystem() {
    return myJarFileSystem;
  }

  public @Nullable VirtualFileSystem getJrtFileSystem() {
    return myJrtFileSystem;
  }
}
