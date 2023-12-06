// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.reporting.FreezeLogger;
import com.intellij.util.SlowOperations;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;

/**
 * Provides services for registering actions which are activated by typing in the editor.
 */
public abstract class TypedAction {
  private static final ExtensionPointName<EditorTypedHandlerBean> EP_NAME = new ExtensionPointName<>("com.intellij.editorTypedHandler");
  private static final ExtensionPointName<EditorTypedHandlerBean> RAW_EP_NAME = new ExtensionPointName<>("com.intellij.rawEditorTypedHandler");

  private TypedActionHandler myRawHandler;
  private @NotNull TypedActionHandler myHandler;
  private boolean myHandlersLoaded;

  public static TypedAction getInstance() {
    return ApplicationManager.getApplication().getService(TypedAction.class);
  }

  public TypedAction() {
    myHandler = new Handler();
  }

  private void ensureHandlersLoaded() {
    if (myHandlersLoaded) {
      return;
    }

    myHandlersLoaded = true;
    EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      TypedActionHandler handler = getOrCreateHandler(bean, myHandler, pluginDescriptor);
      if (handler != null) {
        myHandler = handler;
      }
      return Unit.INSTANCE;
    });
  }

  private static @Nullable TypedActionHandler getOrCreateHandler(@SuppressWarnings("deprecation") @NotNull EditorTypedHandlerBean bean,
                                                                 @NotNull TypedActionHandler originalHandler,
                                                                 @NotNull PluginDescriptor pluginDescriptor) {
    TypedActionHandler handler = bean.handler;
    if (handler != null) {
       return handler;
     }

    try {
      Class<TypedActionHandler> aClass = ApplicationManager.getApplication().loadClass(bean.implementationClass, pluginDescriptor);
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
      Logger.getInstance(TypedAction.class).error(e);
      return null;
    }
    catch (Exception e) {
      Logger.getInstance(TypedAction.class).error(new PluginException(e, pluginDescriptor.getPluginId()));
      return null;
    }
  }

  private void loadRawHandlers() {
    RAW_EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      TypedActionHandler handler = getOrCreateHandler(bean, myRawHandler, pluginDescriptor);
      if (handler != null) {
        myRawHandler = handler;
      }
      return Unit.INSTANCE;
    });
  }

  private static final class Handler implements TypedActionHandler {
    @Override
    public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
      if (editor.isViewer()) {
        return;
      }

      Document doc = editor.getDocument();
      doc.startGuardedBlockChecking();
      try {
        final String str = String.valueOf(charTyped);
        CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.in.editor.command.name"));
        EditorModificationUtil.typeInStringAtCaretHonorMultipleCarets(editor, str, true);
      }
      catch (ReadOnlyFragmentModificationException e) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
      }
      finally {
        doc.stopGuardedBlockChecking();
      }
    }
  }

  /**
   * Gets the current typing handler.
   *
   * @return the current typing handler.
   */
  public @NotNull TypedActionHandler getHandler() {
    ensureHandlersLoaded();
    return myHandler;
  }

  /**
   * Replaces the typing handler with the specified handler. The handler should pass
   * unprocessed typing to the previously registered handler.
   *
   * @param handler the handler to set.
   * @return the previously registered handler.
   * @deprecated Use &lt;typedHandler&gt; extension point for registering typing handlers
   */
  @Deprecated
  public @NotNull TypedActionHandler setupHandler(@NotNull TypedActionHandler handler) {
    ensureHandlersLoaded();
    TypedActionHandler tmp = myHandler;
    myHandler = handler;
    return tmp;
  }

  /**
   * Gets the current 'raw' typing handler.
   *
   * @see #setupRawHandler(TypedActionHandler)
   */
  public @NotNull TypedActionHandler getRawHandler() {
    return myRawHandler;
  }

  /**
   * Replaces current 'raw' typing handler with the specified handler. The handler should pass unprocessed typing to the
   * previously registered 'raw' handler.
   * <p>
   * 'Raw' handler is a handler directly invoked by the code which handles typing in editor. Default 'raw' handler
   * performs some generic logic that has to be done on typing (like checking whether file has write access, creating a command
   * instance for undo subsystem, initiating write action, etc), but delegates to 'normal' handler for actual typing logic.
   *
   * @param handler the handler to set.
   * @return the previously registered handler.
   *
   * @see #getRawHandler()
   * @see #getHandler()
   * @see #setupHandler(TypedActionHandler)
   */
  public TypedActionHandler setupRawHandler(@NotNull TypedActionHandler handler) {
    TypedActionHandler tmp = myRawHandler;
    myRawHandler = handler;
    if (tmp == null) {
      loadRawHandlers();
    }
    return tmp;
  }

  public void beforeActionPerformed(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan) {
    if (myRawHandler instanceof TypedActionHandlerEx) {
      ((TypedActionHandlerEx)myRawHandler).beforeExecute(editor, c, context, plan);
    }
  }

  public final void actionPerformed(final @NotNull Editor editor, final char charTyped, @NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      FreezeLogger.getInstance().runUnderPerformanceMonitor(
        project, () -> myRawHandler.execute(editor, charTyped, dataContext)
      );
    }
  }
}