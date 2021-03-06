// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services for registering actions which are activated by typing in the editor.
 */
public abstract class TypedAction {
  private static final Logger LOG = Logger.getInstance(EditorActionHandlerBean.class);

  private static final ExtensionPointName<EditorTypedHandlerBean> EP_NAME = new ExtensionPointName<>("com.intellij.editorTypedHandler");
  private static final ExtensionPointName<EditorTypedHandlerBean> RAW_EP_NAME = new ExtensionPointName<>("com.intellij.rawEditorTypedHandler");

  private TypedActionHandler myRawHandler;
  private TypedActionHandler myHandler;
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
    DefaultPicoContainer container = new DefaultPicoContainer((DefaultPicoContainer)ApplicationManager.getApplication().getPicoContainer());
    EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      TypedActionHandler handler = getOrCreateHandler(bean, myHandler, container, pluginDescriptor);
      if (handler != null) {
        myHandler = handler;
      }
    });
  }

  @Nullable
  private static TypedActionHandler getOrCreateHandler(@NotNull EditorTypedHandlerBean bean,
                                                       @NotNull TypedActionHandler originalHandler,
                                                       @NotNull DefaultPicoContainer container,
                                                       @NotNull PluginDescriptor pluginDescriptor) {
    TypedActionHandler handler;
    try {
      container.unregisterComponent(TypedActionHandler.class);
      container.registerComponentInstance(TypedActionHandler.class, originalHandler);
      handler = bean.getHandler(container, pluginDescriptor);
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
    return handler;
  }

  private void loadRawHandlers() {
    DefaultPicoContainer container = new DefaultPicoContainer((DefaultPicoContainer)ApplicationManager.getApplication().getPicoContainer());
    RAW_EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      TypedActionHandler handler = getOrCreateHandler(bean, myRawHandler, container, pluginDescriptor);
      if (handler != null) {
        myRawHandler = handler;
      }
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
  public TypedActionHandler getHandler() {
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
  public TypedActionHandler setupHandler(TypedActionHandler handler) {
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
  @NotNull
  public TypedActionHandler getRawHandler() {
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

  public final void actionPerformed(@Nullable final Editor editor, final char charTyped, @NotNull DataContext dataContext) {
    if (editor == null) return;
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    SlowOperations.allowSlowOperations(() -> FreezeLogger.getInstance().runUnderPerformanceMonitor(
      project, () -> myRawHandler.execute(editor, charTyped, dataContext)
    ));
  }
}