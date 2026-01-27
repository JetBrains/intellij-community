// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

import static com.intellij.openapi.util.text.StringUtil.stripHtml;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class BuildConsoleUtils {
  private static final Logger LOG = Logger.getInstance(BuildConsoleUtils.class);

  @ApiStatus.Internal
  public static @NotNull String getMessageTitle(@NotNull String message) {
    message = stripHtml(message, true);
    int sepIndex = message.indexOf(". ");
    int eolIndex = message.indexOf("\n");
    if (sepIndex < 0 || sepIndex > eolIndex && eolIndex > 0) {
      sepIndex = eolIndex;
    }
    if (sepIndex > 0) {
      message = message.substring(0, sepIndex);
    }
    return StringUtil.trimEnd(message.trim(), '.');
  }

  @ApiStatus.Internal
  public static @NotNull DataContext getDataContext(@NotNull BuildTextConsoleView consoleView) {
    var buildView = findBuildView(consoleView);
    var component = ObjectUtils.notNull(buildView, consoleView);
    return DataManager.getInstance().getDataContext(component);
  }

  @ApiStatus.Experimental
  public static @NotNull DataContext getDataContext(@NotNull Object buildId, @NotNull AbstractViewManager buildListener) {
    BuildView buildView = buildListener.getBuildView(buildId);
    return buildView != null ? new MyDelegatingDataContext(buildView) : DataContext.EMPTY_CONTEXT;
  }

  @ApiStatus.Experimental
  public static @NotNull DataContext getDataContext(@NotNull Object buildId, @NotNull BuildProgressListener buildListener,
                                                    @Nullable ComponentContainer container) {
    DataContext dataContext;
    if (buildListener instanceof BuildView) {
      dataContext = new MyDelegatingDataContext((BuildView)buildListener);
    }
    else if (buildListener instanceof AbstractViewManager) {
      dataContext = getDataContext(buildId, (AbstractViewManager)buildListener);
    }
    else if (container != null) {
      dataContext = new MyDelegatingDataContext(container);
    }
    else {
      LOG.error("BuildView or AbstractViewManager expected to obtain proper DataContext for build console quick fixes, " +
                "listener class: " + buildListener.getClass().getName() + ", container: " + container);
      dataContext = DataContext.EMPTY_CONTEXT;
    }
    return dataContext;
  }

  private static @Nullable BuildView findBuildView(@NotNull Component component) {
    Component parent = component;
    while ((parent = parent.getParent()) != null) {
      if (parent instanceof BuildView) {
        return (BuildView)parent;
      }
    }
    return null;
  }

  private static final class MyDelegatingDataContext implements DataContext {
    private final AtomicNotNullLazyValue<DataContext> myDelegatedDataContextValue;

    private MyDelegatingDataContext(@NotNull ComponentContainer container) {
      myDelegatedDataContextValue = AtomicNotNullLazyValue.createValue(() -> DataManager.getInstance().getDataContext(container.getComponent()));
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      return myDelegatedDataContextValue.getValue().getData(dataId);
    }
  }
}
