// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.SlowOperations;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;

import static com.intellij.openapi.editor.rd.LocalEditorSupportUtil.assertLocalEditorSupport;

/**
 * Provides services for registering actions which are activated by typing in the editor.
 */
public abstract class TypedAction {
  private static final Logger LOG = Logger.getInstance(TypedAction.class);

  @SuppressWarnings("removal")
  private static final ExtensionPointName<EditorTypedHandlerBean> EP_NAME = new ExtensionPointName<>("com.intellij.editorTypedHandler");

  @SuppressWarnings("removal")
  private static final ExtensionPointName<EditorTypedHandlerBean> RAW_EP_NAME = new ExtensionPointName<>("com.intellij.rawEditorTypedHandler");

  public static TypedAction getInstance() {
    return ApplicationManager.getApplication().getService(TypedAction.class);
  }

  private TypedActionHandler rawHandler;
  private @NotNull TypedActionHandler handler;
  private boolean handlersLoaded;

  public TypedAction() {
    handler = new DefaultTypedHandler();
  }

  public void beforeActionPerformed(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan) {
    assertLocalEditorSupport(editor);
    if (rawHandler instanceof TypedActionHandlerEx rawHandlerEx) {
      rawHandlerEx.beforeExecute(editor, c, context, plan);
    }
  }

  public final void actionPerformed(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    assertLocalEditorSupport(editor);
    try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      rawHandler.execute(editor, charTyped, dataContext);
    }
  }

  /**
   * Returns the current 'raw' typing handler.
   */
  public @NotNull TypedActionHandler getRawHandler() {
    return rawHandler;
  }

  /**
   * Returns the current typing handler.
   */
  public @NotNull TypedActionHandler getHandler() {
    ensureHandlersLoaded();
    return handler;
  }

  /**
   * Replaces the current 'raw' typing handler with the specified handler.
   * The handler should pass unprocessed typing to the previously registered 'raw' handler.
   * <p>
   * 'Raw' handler is a handler directly invoked by the code which handles typing in the editor.
   * Default 'raw' handler performs some generic logic that has to be done on typing (like checking whether a file has write access,
   * creating a command instance for the undo subsystem, initiating write action, etc.),
   * but delegates to 'normal' handler for actual typing logic.
   *
   * @param handler the handler to set.
   * @return the previously registered handler.
   * @see #getRawHandler()
   * @see #getHandler()
   * @see #setupHandler(TypedActionHandler)
   */
  public TypedActionHandler setupRawHandler(@NotNull TypedActionHandler handler) {
    var tmp = rawHandler;
    rawHandler = handler;
    if (tmp == null) {
      loadRawHandlers();
    }
    return tmp;
  }

  /**
   * Replaces the typing handler with the specified handler.
   * The handler should pass unprocessed typing to the previously registered handler.
   *
   * @param handler the handler to set.
   * @return the previously registered handler.
   * @deprecated Use &lt;typedHandler&gt; extension point for registering typing handlers
   */
  @Deprecated
  public @NotNull TypedActionHandler setupHandler(@NotNull TypedActionHandler handler) {
    ensureHandlersLoaded();
    var tmp = this.handler;
    this.handler = handler;
    return tmp;
  }

  private void loadRawHandlers() {
    RAW_EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      var handler = getOrCreateHandler(bean, rawHandler, pluginDescriptor);
      if (handler != null) {
        rawHandler = handler;
      }
      return Unit.INSTANCE;
    });
  }

  private void ensureHandlersLoaded() {
    if (handlersLoaded) {
      return;
    }
    handlersLoaded = true;
    EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      var handler = getOrCreateHandler(bean, this.handler, pluginDescriptor);
      if (handler != null) {
        this.handler = handler;
      }
      return Unit.INSTANCE;
    });
  }

  @SuppressWarnings("removal")
  private static @Nullable TypedActionHandler getOrCreateHandler(
    @NotNull EditorTypedHandlerBean bean,
    TypedActionHandler originalHandler,
    PluginDescriptor pluginDescriptor
  ) {
    var handler = bean.handler;
    if (handler != null) {
      return handler;
    }
    try {
      var aClass = ApplicationManager.getApplication().<TypedActionHandler>loadClass(bean.implementationClass, pluginDescriptor);
      Constructor<TypedActionHandler> constructor;
      try {
        constructor = aClass.getDeclaredConstructor(TypedActionHandler.class);
      }
      catch (NoSuchMethodException ignore) {
        constructor = null;
      }
      if (constructor == null) {
        constructor = aClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        bean.handler = constructor.newInstance();
      }
      else {
        constructor.setAccessible(true);
        bean.handler = constructor.newInstance(originalHandler);
      }
      return bean.handler;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (PluginException e) {
      LOG.error(e);
      return null;
    }
    catch (Exception e) {
      LOG.error(new PluginException(e, pluginDescriptor.getPluginId()));
      return null;
    }
  }
}
