// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.AdditionalTabComponentManagerEx;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.HashMap;
import java.util.Map;

class AdditionalTabComponentManagerImpl implements AdditionalTabComponentManagerEx {
  private final Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<>();
  private final RunnerLayoutUi myUi;
  private final Icon myIcon;

  AdditionalTabComponentManagerImpl(RunnerLayoutUi ui, Icon defaultIcon) {
    myUi = ui;
    myIcon = defaultIcon;
  }

  @Override
  public void addAdditionalTabComponent(@NotNull AdditionalTabComponent component, @NotNull String id) {
    addAdditionalTabComponent(component, id, myIcon, true);
  }

  @Override
  public @Nullable Content addAdditionalTabComponent(@NotNull AdditionalTabComponent tabComponent,
                                                     @NotNull String id,
                                                     @Nullable Icon icon,
                                                     boolean closeable) {
    Content logContent = myUi.createContent(id, (ComponentWithActions)tabComponent, tabComponent.getTabTitle(), icon,
                                            tabComponent.getPreferredFocusableComponent());
    logContent.setCloseable(closeable);
    myAdditionalContent.put(tabComponent, logContent);
    myUi.addContent(logContent);
    return logContent;
  }

  @Override
  public void removeAdditionalTabComponent(@NotNull AdditionalTabComponent component) {
    Disposer.dispose(component);
    final Content content = myAdditionalContent.remove(component);
    if (!myUi.isDisposed()) {
      myUi.removeContent(content, true);
    }
  }

  @Override
  public void dispose() {
    for (AdditionalTabComponent component : myAdditionalContent.keySet().toArray(new AdditionalTabComponent[0])) {
      removeAdditionalTabComponent(component);
    }
  }
}
