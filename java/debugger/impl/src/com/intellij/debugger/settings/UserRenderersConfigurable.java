// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class UserRenderersConfigurable extends JPanel implements ConfigurableUi<NodeRendererSettings>, Disposable {
  private final JPanel myNameFieldPanel;
  private final JTextField myNameField;
  private final ElementsChooser<NodeRenderer> myRendererChooser;
  private NodeRenderer myCurrentRenderer = null;
  private final CompoundRendererConfigurable myRendererDataConfigurable = new CompoundRendererConfigurable(this);

  public UserRenderersConfigurable() {
    super(new BorderLayout(4, 0));

    myRendererChooser = new ElementsChooser<>(true);
    setupRenderersList();

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator((JTable)myRendererChooser.getComponent());
    decorator.setToolbarPosition(ActionToolbarPosition.TOP);
    decorator.setAddAction(new AddAction());
    decorator.setRemoveAction(new RemoveAction());
    decorator.setMoveUpAction(new MoveAction(true));
    decorator.setMoveDownAction(new MoveAction(false));
    decorator.addExtraAction(new CopyAction());

    myNameField = new JTextField();
    myNameFieldPanel = new JPanel(new BorderLayout());
    myNameFieldPanel.add(new JLabel(JavaDebuggerBundle.message("label.user.renderers.configurable.renderer.name")), BorderLayout.WEST);
    myNameFieldPanel.add(myNameField, BorderLayout.CENTER);
    myNameFieldPanel.setVisible(false);

    final JPanel center = new JPanel(new BorderLayout(0, 4));
    center.add(myNameFieldPanel, BorderLayout.NORTH);
    center.add(myRendererDataConfigurable, BorderLayout.CENTER);

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (myCurrentRenderer != null) {
          myCurrentRenderer.setName(myNameField.getText());
          myRendererChooser.refresh(myCurrentRenderer);
        }
      }
    });

    Splitter splitter = new Splitter(false);
    splitter.setProportion(0.3f);
    splitter.setFirstComponent(decorator.createPanel());
    splitter.setSecondComponent(center);
    add(splitter, BorderLayout.CENTER);
  }

  @Override
  public void dispose() {
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return this;
  }

  private void setupRenderersList() {
    myRendererChooser.getEmptyText().setText(JavaDebuggerBundle.message("text.user.renderers.configurable.no.renderers"));

    myRendererChooser.addElementsMarkListener((ElementsChooser.ElementsMarkListener<NodeRenderer>)NodeRenderer::setEnabled);
    myRendererChooser.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        updateCurrentRenderer(myRendererChooser.getSelectedElements());
      }
    });
  }

  private void updateCurrentRenderer(List<NodeRenderer> selectedElements) {
    if (selectedElements.size() != 1) {
      // multi selection
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
    if (myRendererDataConfigurable.isModified()) {
      myRendererDataConfigurable.apply();
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

  @Override
  public void apply(@NotNull NodeRendererSettings settings) {
    myRendererDataConfigurable.apply();
    flushTo(settings.getCustomRenderers());

    settings.fireRenderersChanged();
  }

  private void flushTo(final RendererConfiguration rendererConfiguration) {
    final int count = myRendererChooser.getElementCount();
    final List<NodeRenderer> renderers = new ArrayList<>(count);
    for (int idx = 0; idx < count; idx++) {
      renderers.add(myRendererChooser.getElementAt(idx));
    }
    rendererConfiguration.setRenderers(renderers);
  }

  @Override
  public boolean isModified(@NotNull NodeRendererSettings settings) {
    if (myRendererDataConfigurable.isModified()) {
      return true;
    }
    final RendererConfiguration rendererConfiguration = settings.getCustomRenderers();
    if (myRendererChooser.getElementCount() != rendererConfiguration.getRendererCount()) {
      return true;
    }
    final RendererConfiguration uiConfiguration = new RendererConfiguration(settings);
    flushTo(uiConfiguration);
    return !uiConfiguration.equals(rendererConfiguration);
  }

  @Override
  public void reset(@NotNull NodeRendererSettings settings) {
    myRendererChooser.removeAllElements();
    final RendererConfiguration rendererConfiguration = settings.getCustomRenderers();
    final ArrayList<NodeRenderer> elementsToSelect = new ArrayList<>(1);
    rendererConfiguration.iterateRenderers(renderer -> {
      final NodeRenderer clonedRenderer = (NodeRenderer)renderer.clone();
      myRendererChooser.addElement(clonedRenderer, clonedRenderer.isEnabled());
      if (elementsToSelect.size() == 0) {
        elementsToSelect.add(clonedRenderer);
      }
      return true;
    });
    myRendererChooser.selectElements(elementsToSelect);
    updateCurrentRenderer(elementsToSelect);
    myRendererDataConfigurable.reset();
  }

  public void addRenderer(NodeRenderer renderer) {
    myRendererChooser.addElement(renderer, renderer.isEnabled());
    myRendererChooser.moveElement(renderer, 0);
  }

  private class AddAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton button) {
      NodeRenderer renderer = (NodeRenderer)NodeRendererSettings.getInstance().createRenderer(CompoundReferenceRenderer.UNIQUE_ID);
      renderer.setEnabled(true);
      addRenderer(renderer);
    }
  }

  private class RemoveAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton button) {
      myRendererChooser.getSelectedElements().forEach(myRendererChooser::removeElement);
    }
  }

  private class CopyAction extends AnActionButton {
    CopyAction() {
      super(JavaDebuggerBundle.messagePointer("button.copy"), JavaDebuggerBundle
        .messagePointer("user.renderers.configurable.button.description.copy"), PlatformIcons.COPY_ICON);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final NodeRenderer selectedElement = myRendererChooser.getSelectedElement();
      if (selectedElement != null) {
        myRendererChooser.addElement((NodeRenderer)selectedElement.clone(), true);
      }
    }

    @Override
    public void updateButton(@NotNull AnActionEvent e) {
      super.updateButton(e);
      e.getPresentation().setEnabled(myRendererChooser.getSelectedElement() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private class MoveAction implements AnActionButtonRunnable {
    private final boolean myMoveUp;

    MoveAction(boolean up) {
      myMoveUp = up;
    }

    @Override
    public void run(AnActionButton button) {
      final int selectedRow = myRendererChooser.getSelectedElementRow();
      if (selectedRow < 0) {
        return;
      }
      int newRow = selectedRow + (myMoveUp ? -1 : 1);
      if (newRow < 0) {
        newRow = myRendererChooser.getElementCount() - 1;
      }
      else if (newRow >= myRendererChooser.getElementCount()) {
        newRow = 0;
      }
      myRendererChooser.moveElement(myRendererChooser.getElementAt(selectedRow), newRow);
    }
  }
}
