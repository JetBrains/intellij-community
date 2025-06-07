package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;

/**
 * @author Liudmila Kornilova
 **/
public class GridFilterAndSortingComponentImpl extends EditorHeaderComponent implements GridFilterAndSortingComponent {
  private static final String FILTER_PREFERRED_SIZE_KEY = "GridFilterComponent.FILTER_PREFERRED_SIZE";
  private final GridFilterPanel myFilterPanel;
  private final GridSortingPanel mySortingPanel;
  private final OnePixelSplitter mySplitter;
  private final DataGrid myGrid;

  public GridFilterAndSortingComponentImpl(@NotNull Project project, @NotNull DataGrid grid) {
    myGrid = grid;
    setBorder(JBUI.Borders.empty());
    mySplitter = new OnePixelSplitter(0.0001f); // proportion will be set in setupFilterSize
    mySplitter.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
    mySplitter.setFocusTraversalPolicyProvider(true);
    setFocusCycleRoot(true);
    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new MyFocusTraversalPolicy(this));
    add(mySplitter, BorderLayout.CENTER);
    myFilterPanel = new GridFilterPanel(project, myGrid);
    setupFilterSize();
    myFilterPanel.myEditor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        myFilterPanel.myEditor.getScrollPane().doLayout(); // update editor width
        mySplitter.revalidate();
        mySplitter.repaint();
      }
    }, myGrid);
    mySplitter.setFirstComponent(myFilterPanel);
    GridSortingModel<GridRow, GridColumn> sortingModel = myGrid.getDataHookup().getSortingModel();
    Document document = sortingModel == null ? null : sortingModel.getDocument();
    CompoundBorder border = new CompoundBorder(new CustomLineBorder(JBColor.border(), 0, 0, 1, 0), JBUI.Borders.empty(6, 6, 5, 6));
    myFilterPanel.setBorder(border);
    mySortingPanel = document == null ? null : new GridSortingPanel(project, myGrid, sortingModel, document);
    if (mySortingPanel != null) {
      mySortingPanel.setBorder(border);
      mySplitter.setSecondComponent(mySortingPanel);
    }
  }

  private void setupFilterSize() {
    UiNotifyConnector.Once.installOn(this, new Activatable() {
      @Override
      public void showNotify() {
        int width = FILTER_PREFERRED_SIZE;
        try {
          String size = PropertiesComponent.getInstance().getValue(FILTER_PREFERRED_SIZE_KEY, Integer.toString(FILTER_PREFERRED_SIZE));
          width = Math.max(FILTER_PREFERRED_SIZE, Integer.parseInt(size));
        }
        catch (NumberFormatException ignored) {
        }
        float proportion = ((float)width) / mySplitter.getWidth();
        mySplitter.setProportion(Math.max(0, Math.min(1f, proportion)));

        mySplitter.addPropertyChangeListener(e -> {
          if (Splitter.PROP_PROPORTION.equals(e.getPropertyName())) {
            PropertiesComponent.getInstance().setValue(FILTER_PREFERRED_SIZE_KEY, Integer.toString(myFilterPanel.getWidth()));
          }
        });
      }
    });
  }

  @Override
  public @NotNull GridFilterPanel getFilterPanel() {
    return myFilterPanel;
  }

  @Override
  public @Nullable GridSortingPanel getSortingPanel() {
    return mySortingPanel;
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public void toggleSortingPanel(boolean visible) {
    if (mySortingPanel == null) return;
    if (visible) mySplitter.setSecondComponent(mySortingPanel);
    else mySplitter.setSecondComponent(null);
  }

  private static class MyFocusTraversalPolicy extends FocusTraversalPolicy {
    private final GridFilterAndSortingComponentImpl myComponent;

    MyFocusTraversalPolicy(GridFilterAndSortingComponentImpl component) {
      myComponent = component;
    }

    @Override
    public Component getComponentAfter(Container container, Component component) {
      return component == myComponent.myFilterPanel.myEditor.getContentComponent()
             ? myComponent.mySortingPanel.myEditor.getContentComponent()
             : myComponent.myGrid.getPreferredFocusedComponent();
    }

    @Override
    public Component getComponentBefore(Container container, Component component) {
      return component == myComponent.mySortingPanel.myEditor.getContentComponent()
             ? myComponent.myFilterPanel.myEditor.getContentComponent()
             : null;
    }

    @Override
    public Component getFirstComponent(Container container) {
      return myComponent.myFilterPanel.myEditor.getContentComponent();
    }

    @Override
    public Component getLastComponent(Container container) {
      return myComponent.mySortingPanel.myEditor.getContentComponent();
    }

    @Override
    public Component getDefaultComponent(Container container) {
      return myComponent.myFilterPanel.myEditor.getContentComponent();
    }
  }
}
