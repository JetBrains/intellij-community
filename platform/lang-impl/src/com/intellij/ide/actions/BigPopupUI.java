// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BigPopupUI<T extends AbstractListModel<Object>> extends BorderLayoutPanel implements Disposable {
  protected final Project myProject;
  protected JBTextField mySearchField;
  protected JPanel suggestionsPanel;
  protected JBList<Object> myResultsList;
  protected JBPopup myHint;
  protected Runnable searchFinishedHandler = () -> {
  };
  protected final List<SearchEverywhereUI.ViewTypeListener> myViewTypeListeners = new ArrayList<>();
  protected SearchEverywhereUI.ViewType myViewType = SearchEverywhereUI.ViewType.SHORT;
  protected T myListModel; //todo using in different threads? #UX-1
  protected JLabel myHintLabel;

  public BigPopupUI(Project project) {
    myProject = project;
  }

  protected abstract void onMouseClicked(@NotNull MouseEvent event);

  @NotNull
  protected abstract T createListModel();

  @NotNull
  public abstract JBList<Object> createList();

  @NotNull
  protected abstract ListCellRenderer createCellRenderer();

  @NotNull
  protected abstract JPanel createTopLeftPanel();

  @NotNull
  protected abstract JPanel createSettingsPanel();

  @NotNull
  protected abstract JBTextField createSearchField();

  protected void initSearchActions() {
    myResultsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        onMouseClicked(e);
      }
    });
  }

  protected void installScrollingActions() {
    ScrollingUtil.installActions(myResultsList, getSearchField());
  }

  public void init() {
    withBackground(JBUI.CurrentTheme.SearchEverywhere.dialogBackground());

    myListModel = createListModel();
    myResultsList = createList();

    JPanel topLeftPanel = createTopLeftPanel();
    JPanel settingsPanel = createSettingsPanel();
    mySearchField = createSearchField();
    suggestionsPanel = createSuggestionsPanel();

    myListModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        updateViewType(SearchEverywhereUI.ViewType.FULL);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        if (myResultsList.isEmpty() && getSearchPattern().isEmpty()) {
          updateViewType(SearchEverywhereUI.ViewType.SHORT);
        }
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        SearchEverywhereUI.ViewType viewType =
          myResultsList.isEmpty() && getSearchPattern().isEmpty() ? SearchEverywhereUI.ViewType.SHORT : SearchEverywhereUI.ViewType.FULL;
        updateViewType(viewType);
      }
    });
    myResultsList.setModel(myListModel);
    myResultsList.setFocusable(false);
    myResultsList.setCellRenderer(createCellRenderer());

    if (Registry.is("new.search.everywhere.use.editor.font")) {
      Font editorFont = EditorUtil.getEditorFont();
      myResultsList.setFont(editorFont);
    }

    installScrollingActions();

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.setOpaque(false);
    topPanel.add(topLeftPanel, BorderLayout.WEST);
    topPanel.add(settingsPanel, BorderLayout.EAST);
    topPanel.add(mySearchField, BorderLayout.SOUTH);

    WindowMoveListener moveListener = new WindowMoveListener(this);
    topPanel.addMouseListener(moveListener);
    topPanel.addMouseMotionListener(moveListener);

    addToTop(topPanel);
    addToCenter(suggestionsPanel);

    initSearchActions();
  }

  @NotNull
  protected String getSearchPattern() {
    return Optional.ofNullable(mySearchField)
      .map(JTextComponent::getText)
      .orElse("");
  }

  protected void updateViewType(@NotNull SearchEverywhereUI.ViewType viewType) {
    if (myViewType != viewType) {
      myViewType = viewType;
      myViewTypeListeners.forEach(listener -> listener.suggestionsShown(viewType));
    }
  }

  private JPanel createSuggestionsPanel() {
    JPanel pnl = new JPanel(new BorderLayout());
    pnl.setOpaque(false);
    //todo
    pnl.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.SearchEverywhere.searchFieldBorderColor(), 1, 0, 0, 0));

    JScrollPane resultsScroll = new JBScrollPane(myResultsList);
    resultsScroll.setBorder(null);
    resultsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    resultsScroll.setPreferredSize(JBUI.size(670, JBUI.CurrentTheme.SearchEverywhere.maxListHeight()));
    pnl.add(resultsScroll, BorderLayout.CENTER);

    myHintLabel = createHint();
    pnl.add(myHintLabel, BorderLayout.SOUTH);

    return pnl;
  }

  @NotNull
  private static JLabel createHint() {
    String hint = IdeBundle.message("searcheverywhere.history.shortcuts.hint",
                                    KeymapUtil.getKeystrokeText(SearchTextField.ALT_SHOW_HISTORY_KEYSTROKE),
                                    KeymapUtil.getKeystrokeText(SearchTextField.SHOW_HISTORY_KEYSTROKE));
    JLabel hintLabel = HintUtil.createAdComponent(hint, JBUI.Borders.emptyLeft(8), SwingConstants.LEFT);
    hintLabel.setOpaque(false);
    hintLabel.setForeground(JBColor.GRAY);
    Dimension size = hintLabel.getPreferredSize();
    size.height = JBUI.scale(17);
    hintLabel.setPreferredSize(size);
    return hintLabel;
  }

  @NotNull
  public JTextField getSearchField() {
    return mySearchField;
  }

  @Override
  public Dimension getMinimumSize() {
    return calcPrefSize(SearchEverywhereUI.ViewType.SHORT);
  }

  @Override
  public Dimension getPreferredSize() {
    return calcPrefSize(myViewType);
  }

  private Dimension calcPrefSize(SearchEverywhereUI.ViewType viewType) {
    Dimension size = super.getPreferredSize();
    if (viewType == SearchEverywhereUI.ViewType.SHORT) {
      size.height -= suggestionsPanel.getPreferredSize().height;
    }
    return size;
  }

  public void setSearchFinishedHandler(@NotNull Runnable searchFinishedHandler) {
    this.searchFinishedHandler = searchFinishedHandler;
  }

  public ViewType getViewType() {
    return myViewType;
  }

  public enum ViewType {FULL, SHORT}

  public interface ViewTypeListener {
    void suggestionsShown(@NotNull ViewType viewType);
  }

  public void addViewTypeListener(ViewTypeListener listener) {
    myViewTypeListeners.add(listener);
  }

  public void removeViewTypeListener(ViewTypeListener listener) {
    myViewTypeListeners.remove(listener);
  }

  protected abstract class ShowFilterAction extends ToggleAction implements DumbAware {
    private JBPopup myFilterPopup;

    public ShowFilterAction() {
      super("Filter", "Filter files by type", AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(@NotNull final AnActionEvent e) {
      return myFilterPopup != null && !myFilterPopup.isDisposed();
    }

    @Override
    public void setSelected(@NotNull final AnActionEvent e, final boolean state) {
      if (state) {
        showPopup(e.getInputEvent().getComponent());
      }
      else {
        if (myFilterPopup != null && !myFilterPopup.isDisposed()) {
          myFilterPopup.cancel();
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Icon icon = getTemplatePresentation().getIcon();
      e.getPresentation().setIcon(isActive() ? ExecutionUtil.getLiveIndicator(icon) : icon);
      e.getPresentation().setEnabled(isEnabled());
      e.getPresentation().putClientProperty(SELECTED_PROPERTY, isSelected(e));
    }

    protected abstract boolean isEnabled();

    protected abstract boolean isActive();

    private void showPopup(Component anchor) {
      if (myFilterPopup != null) {
        return;
      }
      JBPopupListener popupCloseListener = new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          myFilterPopup = null;
        }
      };
      myFilterPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(createFilterPanel(), null)
        .setModalContext(false)
        .setFocusable(false)
        .setResizable(true)
        .setCancelOnClickOutside(false)
        .setMinSize(new Dimension(200, 200))
        .setDimensionServiceKey(myProject, "Search_Everywhere_Filter_Popup", false)
        .addListener(popupCloseListener)
        .createPopup();
      Disposer.register(BigPopupUI.this, myFilterPopup);
      myFilterPopup.showUnderneathOf(anchor);
    }

    private JComponent createFilterPanel() {
      ElementsChooser<?> chooser = createChooser();

      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.add(chooser);
      JPanel buttons = new JPanel();
      JButton all = new JButton("All");
      all.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.setAllElementsMarked(true);
        }
      });
      buttons.add(all);
      JButton none = new JButton("None");
      none.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.setAllElementsMarked(false);
        }
      });
      buttons.add(none);
      JButton invert = new JButton("Invert");
      invert.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.invertSelection();
        }
      });
      buttons.add(invert);
      panel.add(buttons);
      return panel;
    }

    protected abstract ElementsChooser<?> createChooser();
  }
}