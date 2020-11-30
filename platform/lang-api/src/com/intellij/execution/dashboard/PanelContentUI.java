// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.openapi.util.Conditions;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.content.*;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

/**
 * PanelContentUI simply shows selected content in a panel.
 *
 * @author konstantin.aleev
 */
final class PanelContentUI implements ContentUI {
  private JPanel myPanel;
  private ContentManager myContentManager;

  PanelContentUI() {
  }

  @Override
  public JComponent getComponent() {
    initUI();
    return myPanel;
  }

  @Override
  public void setManager(@NotNull ContentManager manager) {
    assert myContentManager == null;
    myContentManager = manager;
    manager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void selectionChanged(@NotNull final ContentManagerEvent event) {
        initUI();
        if (ContentManagerEvent.ContentOperation.add == event.getOperation()) {
          showContent(event.getContent());
        }
        else if (ContentManagerEvent.ContentOperation.remove == event.getOperation()) {
          hideContent();
        }
      }
    });
  }

  private void initUI() {
    if (myPanel != null) {
      return;
    }
    myPanel = new JPanel(new BorderLayout());
    ComponentUtil
      .putClientProperty(myPanel, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, (Iterable<? extends Component>)(Iterable<JComponent>)() -> {
        if (myContentManager == null || myContentManager.getContentCount() == 0) {
          return Collections.emptyIterator();
        }
        return JBIterable.of(myContentManager.getContents())
          .map(content -> {
            JComponent component = content.getComponent();
            return myPanel != component.getParent() ? component : null;
          })
          .filter(Conditions.notNull())
          .iterator();
      });
  }

  private void showContent(@NotNull Content content) {
    if (myPanel.getComponentCount() != 1 ||
        myPanel.getComponent(0) != content.getComponent()) {
      myPanel.removeAll();
      myPanel.add(content.getComponent(), BorderLayout.CENTER);

      myPanel.revalidate();
      myPanel.repaint();
    }
  }

  private void hideContent() {
    myPanel.removeAll();
    myPanel.revalidate();
    myPanel.repaint();
  }

  @Override
  public boolean isSingleSelection() {
    return true;
  }

  @Override
  public boolean isToSelectAddedContent() {
    return true;
  }

  @Override
  public boolean canBeEmptySelection() {
    return false;
  }

  @Override
  public boolean canChangeSelectionTo(@NotNull Content content, boolean implicit) {
    return true;
  }

  @NotNull
  @Override
  public String getCloseActionName() {
    return "";
  }

  @NotNull
  @Override
  public String getCloseAllButThisActionName() {
    return "";
  }

  @NotNull
  @Override
  public String getPreviousContentActionName() {
    return "";
  }

  @NotNull
  @Override
  public String getNextContentActionName() {
    return "";
  }
}
