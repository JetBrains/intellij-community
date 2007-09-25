package com.intellij.mock;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ModalityInvokator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class MockApplication extends MockComponentManager implements ApplicationEx {
  private ModalityState MODALITY_STATE_NONE;
  public MockApplication() {
    super(null);
  }

  public String getName() {
    return "mock";
  }

  public boolean holdsReadLock() {
    return false;
  }

  public void load(String path) throws IOException, InvalidDataException {
  }

  public boolean isInternal() {
    return false;
  }

  public boolean isDispatchThread() {
    return true;
  }



  public void setupIdeQueue(EventQueue queue) {
  }

  //used in Fabrique
  public boolean isExceptionalThreadWithReadAccess(Thread thread) {
    return false;
  }

  public void exit(boolean force) {
  }

  public String getComponentsDescriptor() {
    return null;
  }

  public void assertReadAccessAllowed() {
  }

  public void assertWriteAccessAllowed() {
  }

  public boolean isReadAccessAllowed() {
    return true;
  }

  public boolean isWriteAccessAllowed() {
    return true;
  }

  public boolean isUnitTestMode() {
    return true;
  }

  public boolean isHeadlessEnvironment() {
    return true;
  }

  public boolean isCommandLine() {
    return true;
  }

  public IdeaPluginDescriptor getPlugin(PluginId id) {
    return null;
  }

  public IdeaPluginDescriptor[] getPlugins() {
    return new IdeaPluginDescriptor[0];
  }


  public Future<?> executeOnPooledThread(Runnable action) {
    new Thread(action).start();
    return null; // ?
  }

  public boolean isDisposeInProgress() {
    return false;
  }


  public void runReadAction(Runnable action) {
    action.run();
  }

  public <T> T runReadAction(Computable<T> computation) {
    return computation.compute();
  }

  public void runWriteAction(Runnable action) {
    action.run();
  }

  public <T> T runWriteAction(Computable<T> computation) {
    return computation.compute();
  }

  public Object getCurrentWriteAction(Class actionClass) {
    return null;
  }

  public void assertIsDispatchThread() {
  }

  public void addApplicationListener(ApplicationListener listener) {
  }

  public void removeApplicationListener(ApplicationListener listener) {
  }

  public void saveAll() {
  }

  public void saveSettings() {
  }

  public void exit() {
  }

  public void assertReadAccessToDocumentsAllowed() {
  }

  public void doNotSave() {
  }

  public boolean isDoNotSave() {
    return false; 
  }

  public boolean runProcessWithProgressSynchronously(Runnable process,
                                                     String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project) {
    return false;
  }

  public boolean runProcessWithProgressSynchronously(Runnable process,
                                                     String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project,
                                                     boolean smoothProgress) {
    return false;
  }

  public void invokeLater(final Runnable runnable, @NotNull final Condition expired) {
  }

  public void invokeLater(final Runnable runnable, @NotNull final ModalityState state, @NotNull final Condition expired) {
  }

  public void invokeLater(Runnable runnable) {
  }

  public void invokeLater(Runnable runnable, ModalityState state) {
  }

  @NotNull
  public ModalityInvokator getInvokator() {
    throw new UnsupportedOperationException();
  }

  public void invokeAndWait(Runnable runnable, ModalityState modalityState) {
  }

  public long getStartTime() {
    return 0;
  }

  public long getIdleTime() {
    return 0;
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  public ModalityState getCurrentModalityState() {
    return null;
  }

  public ModalityState getModalityStateForComponent(Component c) {
    return null;
  }

  public ModalityState getDefaultModalityState() {
    return null;
  }

  public ModalityState getNoneModalityState() {
    if (MODALITY_STATE_NONE == null) {
      MODALITY_STATE_NONE = new ModalityStateEx(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    return MODALITY_STATE_NONE;
  }

  public <T> List<Future<T>> invokeAllUnderReadAction(@NotNull final Collection<Callable<T>> tasks, final ExecutorService executorService)
    throws Throwable {
    return null;
  }
}
