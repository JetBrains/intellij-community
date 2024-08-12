// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.Advertiser;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.Optional;

public abstract class BigPopupUI extends BorderLayoutPanel implements Disposable {
  private static final int MINIMAL_SUGGESTIONS_LIST_HEIGHT= 100;

  protected final @Nullable Project myProject;

  protected ExtendableTextField mySearchField;
  protected JPanel suggestionsPanel;
  protected JBList<Object> myResultsList;
  protected JBPopup myHint;
  protected Runnable searchFinishedHandler = () -> { };
  protected final List<ViewTypeListener> myViewTypeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  protected ViewType myViewType = ViewType.SHORT;
  protected Advertiser myHintLabel;

  public BigPopupUI(@Nullable Project project) {
    myProject = project;
  }

  public abstract @NotNull JBList<Object> createList();

  protected abstract @NotNull ListCellRenderer<Object> createCellRenderer();

  /**
   * todo make the method abstract after {@link #createTopLeftPanel()} and {@link #createSettingsPanel()} are removed
   */
  protected @NotNull JComponent createHeader() {
    JPanel header = new JPanel(new BorderLayout());
    header.add(createTopLeftPanel(), BorderLayout.WEST);
    header.add(createSettingsPanel(), BorderLayout.EAST);
    return header;
  }

  /**
   * @deprecated Override createHeader and remove implementation of this method at all
   */
  @Deprecated(forRemoval = true)
  protected @NotNull JPanel createTopLeftPanel() {
    return new JPanel(); // not used
  }

  /**
   * @deprecated Override createHeader and remove implementation of this method at all
   */
  @Deprecated(forRemoval = true)
  protected @NotNull JPanel createSettingsPanel() {
    return new JPanel(); // not used
  }

  protected @PopupAdvertisement String @NotNull [] getInitialHints() {
    String hint = getInitialHint();
    return hint != null ? new String[]{hint} : ArrayUtil.EMPTY_STRING_ARRAY;
  }
  
  protected @Nullable @PopupAdvertisement String getInitialHint() {
    return null;
  }

  protected abstract @NotNull @Nls String getAccessibleName();

  protected void installScrollingActions() {
    ScrollingUtil.installActions(myResultsList, getSearchField());
  }

  protected static class SearchField extends ExtendableTextField {
    public SearchField() {
      if (ExperimentalUI.isNewUI()) {
        setOpaque(false);
        setBorder(PopupUtil.createComplexPopupTextFieldBorder());
      }
      else {
        Border empty = new EmptyBorder(JBUI.CurrentTheme.BigPopup.searchFieldInsets());
        Border topLine = JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.searchFieldBorderColor(), 1, 0, 0, 0);
        setBorder(JBUI.Borders.merge(empty, topLine, true));
        setBackground(JBUI.CurrentTheme.BigPopup.searchFieldBackground());
      }
      setFocusTraversalKeysEnabled(false);

      if (Registry.is("new.search.everywhere.use.editor.font")) {
        Font editorFont = EditorUtil.getEditorFont();
        setFont(editorFont);
      }

      int fontDelta = Registry.intValue("new.search.everywhere.font.size.delta");
      if (fontDelta != 0) {
        Font font = getFont();
        font = font.deriveFont((float)fontDelta + font.getSize());
        setFont(font);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = Integer.max(JBUIScale.scale(29), size.height);
      return size;
    }
  }

  protected @NotNull ExtendableTextField createSearchField() {
    return new SearchField();
  }

  public void init() {
    myResultsList = createList();

    mySearchField = createSearchField();
    suggestionsPanel = createSuggestionsPanel();

    myResultsList.setFocusable(false);
    myResultsList.setCellRenderer(createCellRenderer());

    if (Registry.is("new.search.everywhere.use.editor.font")) {
      Font editorFont = EditorUtil.getEditorFont();
      myResultsList.setFont(editorFont);
    }

    installScrollingActions();

    JComponent header = createHeader();
    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.setOpaque(false);
    topPanel.add(header, BorderLayout.NORTH);
    topPanel.add(mySearchField, BorderLayout.SOUTH);

    new WindowMoveListener(this).installTo(topPanel);

    addToTop(topPanel);
    addToCenter(suggestionsPanel);

    if (ExperimentalUI.isNewUI()) {
      setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
      if (header.getBorder() == null) {
        header.setBorder(
          JBUI.Borders.compound(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 1, 0),
                                new EmptyBorder(JBUI.CurrentTheme.ComplexPopup.headerInsets())));
      }
      header.setBackground(JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND);
      myResultsList.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
    }
    else {
      suggestionsPanel.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.searchFieldBorderColor(), 1, 0, 0, 0));
      setBackground(JBUI.CurrentTheme.BigPopup.headerBackground());
    }
    getAccessibleContext().setAccessibleName(getAccessibleName());
  }

  protected void addListDataListener(@NotNull AbstractListModel<Object> model) {
    model.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        updateViewType(ViewType.FULL);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        if (myResultsList.isEmpty() && getSearchPattern().isEmpty()) {
          updateViewType(ViewType.SHORT);
        }
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        updateViewType(myResultsList.isEmpty() && getSearchPattern().isEmpty() ? ViewType.SHORT : ViewType.FULL);
      }
    });
  }

  protected @NotNull String getSearchPattern() {
    return Optional.ofNullable(mySearchField)
      .map(JTextComponent::getText)
      .orElse("");
  }

  protected void updateViewType(@NotNull ViewType viewType) {
    if (myViewType != viewType) {
      myViewType = viewType;
      myViewTypeListeners.forEach(listener -> listener.suggestionsShown(viewType));
    }
  }

  protected JPanel createSuggestionsPanel() {
    JPanel pnl = new JPanel(new BorderLayout());
    pnl.setOpaque(false);

    JScrollPane resultsScroll = createListPane();
    pnl.add(resultsScroll, BorderLayout.CENTER);

    return createFooterPanel(pnl);
  }

  protected @NotNull JScrollPane createListPane() {
    JScrollPane resultsScroll = new JBScrollPane(myResultsList) {
      @Override
      public void updateUI() {
        boolean isBorderNull = getBorder() == null;
        super.updateUI();
        if (isBorderNull) setBorder(null);
      }
    };
    resultsScroll.setBorder(null);
    resultsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    ComponentUtil.putClientProperty(resultsScroll.getVerticalScrollBar(), JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true);

    resultsScroll.setPreferredSize(JBUI.size(670, JBUI.CurrentTheme.BigPopup.maxListHeight()));
    return resultsScroll;
  }

  protected @NotNull JPanel createFooterPanel(@NotNull JPanel panel) {
    myHintLabel = createHint();
    panel.add(myHintLabel.getAdComponent(), BorderLayout.SOUTH);
    return panel;
  }

  private @NotNull Advertiser createHint() {
    Advertiser advertiser = new Advertiser();

    advertiser.setBorder(JBUI.CurrentTheme.BigPopup.advertiserBorder());
    advertiser.setBackground(JBUI.CurrentTheme.BigPopup.advertiserBackground());
    advertiser.setForeground(JBUI.CurrentTheme.BigPopup.advertiserForeground());
    
    for (@PopupAdvertisement String s : getInitialHints()) {
      advertiser.addAdvertisement(s, null);  
    }
    advertiser.showRandomText();
    return advertiser;
  }

  public @NotNull JTextField getSearchField() {
    return mySearchField;
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = calcPrefSize(ViewType.SHORT);
    if (getViewType() == ViewType.FULL) {
      size.height += MINIMAL_SUGGESTIONS_LIST_HEIGHT;
    }
    return size;
  }

  @Override
  public Dimension getPreferredSize() {
    return calcPrefSize(myViewType);
  }

  public Dimension getExpandedSize() {
    return calcPrefSize(ViewType.FULL);
  }

  private Dimension calcPrefSize(ViewType viewType) {
    Dimension size = super.getPreferredSize();
    if (viewType == ViewType.SHORT) {
      size.height -= suggestionsPanel.getPreferredSize().height;
      if (ExperimentalUI.isNewUI()) {
        size.height -= JBUI.CurrentTheme.ComplexPopup.textFieldBorderInsets().bottom +
                       JBUI.scale(JBUI.CurrentTheme.ComplexPopup.TEXT_FIELD_SEPARATOR_HEIGHT);
      }
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
}