/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.tree.render.CompoundNodeRenderer;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.InternalIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 19, 2005
 */
public class UserRenderersConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.UserRenderersConfigurable");
  private static final Icon ADD_ICON = IconUtil.getAddIcon();
  private static final Icon REMOVE_ICON = IconUtil.getRemoveIcon();
  private static final Icon COPY_ICON = PlatformIcons.COPY_ICON;
  private static final Icon UP_ICON = IconUtil.getMoveUpIcon();
  private static final Icon DOWN_ICON = IconUtil.getMoveDownIcon();

  private JPanel myNameFieldPanel;
  private JTextField myNameField;
  private ElementsChooser<NodeRenderer> myRendererChooser;
  private NodeRenderer myCurrentRenderer = null;
  private final CompoundRendererConfigurable myRendererDataConfigurable;

  public UserRenderersConfigurable(@Nullable Project project) {
    myRendererDataConfigurable = new CompoundRendererConfigurable(project);
  }

  public String getDisplayName() {
    return DebuggerBundle.message("user.renderers.configurable.display.name");
  }

  public String getHelpTopic() {
    return "reference.idesettings.debugger.typerenderers"; 
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new BorderLayout(4, 0));

    final JComponent renderersList = createRenderersList();
    final JComponent toolbar = createToolbar();
    final JComponent rendererDataPanel = myRendererDataConfigurable.createComponent();

    final JPanel left = new JPanel(new BorderLayout());

    left.add(toolbar, BorderLayout.NORTH);
    left.add(renderersList, BorderLayout.CENTER);

    myNameField = new JTextField();
    myNameFieldPanel = new JPanel(new BorderLayout());
    myNameFieldPanel.add(new JLabel(DebuggerBundle.message("label.user.renderers.configurable.renderer.name")), BorderLayout.WEST);
    myNameFieldPanel.add(myNameField, BorderLayout.CENTER);
    myNameFieldPanel.setVisible(false);

    final JPanel center = new JPanel(new BorderLayout(0, 4));

    center.add(myNameFieldPanel, BorderLayout.NORTH);
    center.add(rendererDataPanel, BorderLayout.CENTER);

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (myCurrentRenderer != null) {
          myCurrentRenderer.setName(myNameField.getText());
          myRendererChooser.refresh(myCurrentRenderer);
        }
      }
    });

    panel.add(left, BorderLayout.WEST);
    panel.add(center, BorderLayout.CENTER);

    return panel;
  }

  private JComponent createRenderersList() {
    myRendererChooser = new ElementsChooser<NodeRenderer>(true);
    myRendererChooser.getEmptyText().setText(DebuggerBundle.message("text.user.renderers.configurable.no.renderers"));

    myRendererChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<NodeRenderer>() {
      public void elementMarkChanged(final NodeRenderer element, final boolean isMarked) {
        element.setEnabled(isMarked);
      }
    });
    myRendererChooser.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          updateCurrentRenderer(myRendererChooser.getSelectedElements());
        }
      }
    });
    return myRendererChooser;
  }

  private void updateCurrentRenderer(List<NodeRenderer> selectedElements) {
    if (selectedElements.size() != 1) {
      // multiselection
      setCurrentRenderer(null);
    }
    else {
      setCurrentRenderer(selectedElements.get(0));
    }
  }

  private void setCurrentRenderer(NodeRenderer renderer) {
    if (myCurrentRenderer == renderer) {
      return;
    }
    try {
      if (myRendererDataConfigurable.isModified()) {
        myRendererDataConfigurable.apply();
      }
    }
    catch (ConfigurationException e) {
      LOG.error(e);
    }
    myCurrentRenderer = renderer;
    if (renderer != null) {
      myNameFieldPanel.setVisible(true);
      myNameField.setText(renderer.getName());
    }
    else {
      myNameFieldPanel.setVisible(false);
      myNameField.setText("");
    }
    myRendererDataConfigurable.setRenderer(renderer);
  }

  private JComponent createToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddAction());
    group.add(new RemoveAction());
    group.add(new CopyAction());
    group.add(new MoveAction(true));
    group.add(new MoveAction(false));
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  public void apply() throws ConfigurationException {
    myRendererDataConfigurable.apply();
    flushTo(NodeRendererSettings.getInstance().getCustomRenderers());

    NodeRendererSettings.getInstance().fireRenderersChanged();
  }

  private void flushTo(final RendererConfiguration rendererConfiguration) {
    final int count = myRendererChooser.getElementCount();
    final List<NodeRenderer> renderers = new ArrayList<NodeRenderer>(count);
    for (int idx = 0; idx < count; idx++) {
      renderers.add(myRendererChooser.getElementAt(idx));
    }
    rendererConfiguration.setRenderers(renderers);
  }

  public boolean isModified() {
    if (myRendererDataConfigurable.isModified()) {
      return true;
    }
    final NodeRendererSettings settings = NodeRendererSettings.getInstance();
    final RendererConfiguration rendererConfiguration = settings.getCustomRenderers();
    if (myRendererChooser.getElementCount() != rendererConfiguration.getRendererCount()) {
      return true;
    }
    final RendererConfiguration uiConfiguration = new RendererConfiguration(settings);
    flushTo(uiConfiguration);
    return !uiConfiguration.equals(rendererConfiguration);
  }

  public void reset() {
    myRendererChooser.removeAllElements();
    final RendererConfiguration rendererConfiguration = NodeRendererSettings.getInstance().getCustomRenderers();
    final ArrayList<NodeRenderer> elementsToSelect = new ArrayList<NodeRenderer>(1);
    rendererConfiguration.iterateRenderers(new InternalIterator<NodeRenderer>() {
      public boolean visit(final NodeRenderer renderer) {
        final NodeRenderer clonedRenderer = (NodeRenderer)renderer.clone();
        myRendererChooser.addElement(clonedRenderer, clonedRenderer.isEnabled());
        if (elementsToSelect.size() == 0) {
          elementsToSelect.add(clonedRenderer);
        }
        return true;
      }
    });
    myRendererChooser.selectElements(elementsToSelect);
    updateCurrentRenderer(elementsToSelect);
    myRendererDataConfigurable.reset();
  }

  public void disposeUIResources() {
    myRendererChooser.removeAllElements();
    myRendererDataConfigurable.disposeUIResources();
  }

  private class AddAction extends AnAction {
    public AddAction() {
      super(DebuggerBundle.message("button.add"), DebuggerBundle.message("user.renderers.configurable.button.description.add"), ADD_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      final NodeRenderer renderer = (NodeRenderer)NodeRendererSettings.getInstance().createRenderer(CompoundNodeRenderer.UNIQUE_ID);
      renderer.setEnabled(true);
      myRendererChooser.addElement(renderer, renderer.isEnabled());
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myNameField.requestFocus();
        }
      });
    }
  }

  private class RemoveAction extends AnAction {
    public RemoveAction() {
      super(DebuggerBundle.message("button.remove"), DebuggerBundle.message("user.renderers.configurable.button.description.remove"), REMOVE_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      for (NodeRenderer selectedElement : myRendererChooser.getSelectedElements()) {
        myRendererChooser.removeElement(selectedElement);
      }
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myRendererChooser.getSelectedElement() != null);
    }
  }

  private class CopyAction extends AnAction {
    public CopyAction() {
      super(DebuggerBundle.message("button.copy"), DebuggerBundle.message("user.renderers.configurable.button.description.copy"), COPY_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      final NodeRenderer selectedElement = myRendererChooser.getSelectedElement();
      if (selectedElement != null) {
        final NodeRenderer cloned = (NodeRenderer)selectedElement.clone();
        myRendererChooser.addElement(cloned, true);
      }
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myRendererChooser.getSelectedElement() != null);
    }
  }

  private class MoveAction extends AnAction {
    private final boolean myMoveUp;

    public MoveAction(boolean up) {
      super(up? DebuggerBundle.message("button.move.up") : DebuggerBundle.message("button.move.down"),
            up? DebuggerBundle.message("user.renderers.configurable.button.description.move.up") : DebuggerBundle.message("user.renderers.configurable.button.description.move.down"),
            up? UP_ICON : DOWN_ICON );
      myMoveUp = up;
    }

    public void actionPerformed(AnActionEvent e) {
      final int selectedRow = myRendererChooser.getSelectedElementRow();
      if (selectedRow < 0) {
        return;
      }
      int newRow = selectedRow + (myMoveUp? -1 : 1);
      if (newRow < 0) {
        newRow = myRendererChooser.getElementCount() - 1;
      }
      else if (newRow >= myRendererChooser.getElementCount()) {
        newRow = 0;
      }
      myRendererChooser.moveElement(myRendererChooser.getElementAt(selectedRow), newRow);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myRendererChooser.getSelectedElement() != null);
    }
  }
}
