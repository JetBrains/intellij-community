// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class EditorGui {

  public enum State {
    NORMAL_PAGE_VIEW,
    LOADING_FILE,
    LOADING_PAGE,
    ERROR_OPENING
  }

  public enum SearchPanelsViewState {
    ALL_HIDDEN,
    ONLY_MANAGING_PANEL,
  }

  final String HEADER_NAVIGATION_CARD = "panelNavigationCard";
  final String HEADER_LOADING_CARD = "panelLoadingCard";
  final String HEADER_MSG_ERROR_CARD = "panelMsgErrorCard";
  final String EDITOR_PAGE_VIEW_CARD = "panelEditorPageViewCard";
  final String EDITOR_LOADING_CARD = "panelEditorLoadingCard";
  final String EDITOR_EMPTY_CARD = "panelEditorEmptyCard";

  JPanel panelMain;
  JPanel panelEditorPageViewCard;
  JTextField txtbxCurPageNum;
  JLabel lblTextAfterCurPageNum;
  JPanel panelNavigationCard;
  JPanel panelHeader;
  JPanel panelLoadingCard;
  JPanel panelMsgErrorCard;
  JLabel lblMsgLoading;
  JPanel panelEditorBody;
  JPanel panelEditorLoadingCard;
  JLabel lblEditorLoading;
  JPanel panelEditorEmptyCard;
  JPanel panelViewPage;
  JPanel panelEditor;
  JPanel panelEditorHeader;
  JProgressBar prbrSavingStatus;
  JPanel panelSavingStatus;
  JLabel lblSavingStatus;
  JPanel panelNavigationBtns;
  JPanel panelSaveActions;
  JPanel panelMsgError;
  JLabel lblPositionPercents;

  private SearchPanelsViewState currentSearchPanelsViewState = SearchPanelsViewState.ALL_HIDDEN;
  private State currentViewState = State.ERROR_OPENING;

  public EditorGui() {
    setBorderToHeaderPanel(panelHeader);

    setSelectingAllTextWhenFocusGained(txtbxCurPageNum);

    lblPositionPercents.setForeground(JBColor.GRAY);
  }

  public JComponent getRootComponent() {
    return panelMain;
  }

  public void setEditorComponent(Editor editor) {
    panelViewPage.add(editor.getComponent(), BorderLayout.CENTER);
  }

  public void setEditorHeaderComponent(JComponent editorHeaderComponent) {
    panelEditorHeader.add(editorHeaderComponent, BorderLayout.CENTER);
  }

  public void setSearchPanelsViewState(SearchPanelsViewState searchPanelsViewState) {
    // DEBUG BEGIN
    // TO DO - delete next debug line
        /*if (searchPanelsViewState == SearchPanelsViewState.ONLY_MANAGING_PANEL)
            searchPanelsViewState = SearchPanelsViewState.MANAGING_AND_RESULTS_PANELS;*/
    // DEBUG  END

    if (searchPanelsViewState != currentSearchPanelsViewState) {
      currentSearchPanelsViewState = searchPanelsViewState;
      updateViewState();
    }
  }

  public SearchPanelsViewState getCurrentSearchPanelsViewState() {
    return currentSearchPanelsViewState;
  }

  public void setViewState(State state) {
    if (currentViewState != state) {
      currentViewState = state;
      updateViewState();
    }
  }

  public void setVisibleSavingStatusPanel(boolean visible) {
    panelSavingStatus.setVisible(visible);
  }

  public void setSavingProgress(int i) {
    prbrSavingStatus.setValue(i);
  }

  public void setSavingStatusText(String s) {
    lblSavingStatus.setText(s);
  }

  private void updateViewState() {
    switch (currentViewState) {
      case NORMAL_PAGE_VIEW:
        setCard(panelHeader, HEADER_NAVIGATION_CARD);
        setCard(panelEditorBody, EDITOR_PAGE_VIEW_CARD);
        panelEditorHeader.setVisible(
          currentSearchPanelsViewState == SearchPanelsViewState.ONLY_MANAGING_PANEL);
        break;
      case LOADING_PAGE:
        setCard(panelHeader, HEADER_NAVIGATION_CARD);
        setCard(panelEditorBody, EDITOR_LOADING_CARD);
        panelEditorHeader.setVisible(
          currentSearchPanelsViewState == SearchPanelsViewState.ONLY_MANAGING_PANEL);
        break;
      case LOADING_FILE:
        setCard(panelHeader, HEADER_LOADING_CARD);
        setCard(panelEditorBody, EDITOR_EMPTY_CARD);
        panelEditorHeader.setVisible(false);
        break;
      case ERROR_OPENING:
        setCard(panelHeader, HEADER_MSG_ERROR_CARD);
        setCard(panelEditorBody, EDITOR_EMPTY_CARD);
        panelEditorHeader.setVisible(false);
        break;
    }
  }

  private void setCard(JComponent container, String cardName) {
    CardLayout panelHeaderCardLayout = (CardLayout)container.getLayout();
    panelHeaderCardLayout.show(container, cardName);
  }

  private void setBorderToHeaderPanel(JPanel panelHeader) {
    panelHeader.setBorder(new CustomLineBorder(JBColor.border(), 0, 0, 1, 0));
  }

  private void setSelectingAllTextWhenFocusGained(JTextComponent textComponent) {
    textComponent.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        textComponent.selectAll();
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    });
  }
}
