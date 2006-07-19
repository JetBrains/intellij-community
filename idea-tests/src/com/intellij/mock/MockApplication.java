package com.intellij.mock;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

import java.awt.*;
import java.io.IOException;

public class MockApplication extends UserDataHolderBase implements ApplicationEx {
  public String getName() {
    return "mock";
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

  public IdeaPluginDescriptor getPlugin(PluginId id) {
    return null;
  }

  public IdeaPluginDescriptor[] getPlugins() {
    return new IdeaPluginDescriptor[0];
  }

  public boolean isDisposed() {
    return false;
  }

  public BaseComponent getComponent(String name) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    return null;
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

  public void dispose() {
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

  public void invokeLater(Runnable runnable) {
  }

  public void invokeLater(Runnable runnable, ModalityState state) {
  }

  public void invokeAndWait(Runnable runnable, ModalityState modalityState) {
  }

  public long getStartTime() {
    return 0;
  }

  public long getIdleTime() {
    return 0;
  }

  @NotNull
  public Class[] getComponentInterfaces() {
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }

  public boolean hasComponent(Class interfaceClass) {
    return false;
  }

  @NotNull
  public <T> T[] getComponents(Class<T> baseInterfaceClass) {
    return (T[]) ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
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
    return null;
  }
}
