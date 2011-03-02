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

/*
 * @author max
 */
package com.intellij.find;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.find.impl.livePreview.LiveOccurrence;
import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.find.impl.livePreview.LivePreviewControllerBase;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class EditorSearchComponent extends JPanel implements DataProvider, SelectionListener, SearchResults.SearchResultsListener,
                                                             FindModel.FindModelObserver {
  private static final int MATCHES_LIMIT = 10000;
  private final JLabel myMatchInfoLabel;
  private final LinkLabel myClickToHighlightLabel;
  private final Project myProject;
  private final Editor myEditor;
  private final JTextField mySearchField;
  private JTextField myReplaceField;
  private final Color myDefaultBackground;

  private JButton myReplaceButton;
  private JButton myReplaceAllButton;
  private JButton myExcludeButton;

  private JCheckBox myPreserveCase;
  private JCheckBox mySelectionOnly;

  private final Color GRADIENT_C1;
  private final Color GRADIENT_C2;
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);
  private static final Color COMPLETION_BACKGROUND_COLOR = new Color(235, 244, 254);
  private static final Color FOCUS_CATCHER_COLOR = new Color(0x9999ff);
  private final JComponent myToolbarComponent;
  private com.intellij.openapi.editor.event.DocumentAdapter myDocumentListener;

  private final JCheckBox myCbRegexp;
  private final JCheckBox myCbWholeWords;
  private final JCheckBox myCbInComments;
  private final JCheckBox myCbInLiterals;

  private final JPanel myOptionsPane;
  private final LinkLabel myMoreOptionsButton;

  private final MyLivePreviewController myLivePreviewController;
  private final LivePreview myLivePreview;


  private boolean myListeningSelection = false;
  private SearchResults mySearchResults;
  private Balloon myOptionsBalloon;

  private final FindModel myFindModel;
  private JCheckBox myCbMatchCase;
  private JPanel myReplacementPane;

  private static FindModel createDefaultFindModel(Project p, Editor e) {
    FindModel findModel = new FindModel();
    findModel.copyFrom(FindManager.getInstance(p).getFindInFileModel());
    if (e.getSelectionModel().hasSelection()) {
      String selectedText = e.getSelectionModel().getSelectedText();
      if (selectedText != null) {
        findModel.setStringToFind(selectedText);
      }
    }
    return findModel;
  }

  public EditorSearchComponent(Editor editor, Project project) {
    this(editor, project, createDefaultFindModel(project, editor));
  }

  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (PlatformDataKeys.EDITOR_EVEN_IF_INACTIVE.is(dataId)) {
      return myEditor;
    }
    return null;
  }

  @Override
  public void searchResultsUpdated(SearchResults sr) {
    int count = sr.getActualFound();
    if (mySearchField.getText().isEmpty()) {
      nothingToSearchFor();
    } else {
      if (count <= mySearchResults.getMatchesLimit()) {
        myClickToHighlightLabel.setVisible(false);

        if (count > 0) {
          setRegularBackground();
          if (count > 1) {
            myMatchInfoLabel.setText(count + " matches");
          }
          else {
            myMatchInfoLabel.setText("1 match");
          }
        }
        else {
          setNotFoundBackground();
          myMatchInfoLabel.setText("No matches");
        }
      }
      else {
        setRegularBackground();
        myMatchInfoLabel.setText("More than " + mySearchResults.getMatchesLimit() + " matches");
        myClickToHighlightLabel.setVisible(true);
        boldMatchInfo();
      }
    }

    updateExcludeStatus();
  }

  @Override
  public void cursorMoved() {
    updateExcludeStatus();
  }

  @Override
  public void editorChanged(SearchResults sr, Editor oldEditor) {  }

  public EditorSearchComponent(final Editor editor, final Project project, FindModel findModel) {
    super(new BorderLayout(0, 0));
    myFindModel = findModel;

    GRADIENT_C1 = getBackground();
    GRADIENT_C2 = new Color(Math.max(0, GRADIENT_C1.getRed() - 0x18), Math.max(0, GRADIENT_C1.getGreen() - 0x18), Math.max(0, GRADIENT_C1.getBlue() - 0x18));

    myProject = project;
    myEditor = editor;

    mySearchResults = new SearchResults(myEditor);
    myLivePreview = new LivePreview(mySearchResults);

    myLivePreviewController = new MyLivePreviewController();

    mySearchResults.addListener(this);
    setMatchesLimit(MATCHES_LIMIT);


    final JPanel leadPanel = createLeadPane();
    add(leadPanel, BorderLayout.WEST);

    mySearchField = createTextField();

    leadPanel.add(mySearchField);
    mySearchField.putClientProperty("AuxEditorComponent", Boolean.TRUE);

    myDefaultBackground = mySearchField.getBackground();

    DefaultActionGroup group = new DefaultActionGroup("search bar", false);
    group.add(new ShowHistoryAction(mySearchField));
    group.add(new PrevOccurrenceAction());
    group.add(new NextOccurrenceAction());
    group.add(new FindAllAction());

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("SearchBar", group, true);
    tb.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myToolbarComponent = tb.getComponent();
    myToolbarComponent.setBorder(null);
    myToolbarComponent.setOpaque(false);
    leadPanel.add(myToolbarComponent);

    myCbMatchCase = new NonFocusableCheckBox("Case sensitive");
    myCbWholeWords = new NonFocusableCheckBox("Match whole words only");
    myCbRegexp = new NonFocusableCheckBox("Regex");
    myCbInComments = new NonFocusableCheckBox("Search in comments only");
    myCbInLiterals = new NonFocusableCheckBox("Search in literals only");

    myOptionsPane = new JPanel();
    myOptionsPane.setLayout(new BoxLayout(myOptionsPane, BoxLayout.Y_AXIS));

    leadPanel.add(myCbMatchCase);
    myOptionsPane.add(myCbWholeWords);
    leadPanel.add(myCbRegexp);
    if (FindManagerImpl.ourHasSearchInCommentsAndLiterals) {
      myOptionsPane.add(myCbInComments);
      myOptionsPane.add(myCbInLiterals);
    }

    myMoreOptionsButton = new LinkLabel("More options", null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        if (myOptionsBalloon == null || myOptionsBalloon.isDisposed()) {
          BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(myOptionsPane);
          balloonBuilder.setFillColor(myOptionsPane.getBackground());
          Point point = new Point((int)(myMoreOptionsButton.getX() + myMoreOptionsButton.getBounds().getWidth()/2),
                                  (int)(myMoreOptionsButton.getY() + myMoreOptionsButton.getBounds().getHeight()/2));
          myOptionsBalloon = balloonBuilder.createBalloon();
          myOptionsBalloon.show(new RelativePoint(leadPanel, point), Balloon.Position.below);
        }
      }
    });


    leadPanel.add(myMoreOptionsButton);

    myFindModel.addObserver(new FindModel.FindModelObserver() {
      @Override
      public void findModelChanged(FindModel findModel) {
        syncFindModels(FindManager.getInstance(myProject).getFindInFileModel(), myFindModel);
        String stringToFind = myFindModel.getStringToFind();
        if (!wholeWordsApplicable(stringToFind)) {
          myFindModel.setWholeWordsOnly(false);
        }
        updateUIWithFindModel();
        updateResults(true);
      }
    });

    FindManager.getInstance(myProject).getFindInFileModel().addObserver(this);

    updateUIWithFindModel();

    myCbMatchCase.setMnemonic('C');
    myCbWholeWords.setMnemonic('M');
    myCbRegexp.setMnemonic('e');
    myCbInComments.setMnemonic('o');
    myCbInLiterals.setMnemonic('l');

    setSmallerFontAndOpaque(myCbWholeWords);
    setSmallerFontAndOpaque(myCbMatchCase);
    setSmallerFontAndOpaque(myCbRegexp);
    setSmallerFontAndOpaque(myCbInComments);
    setSmallerFontAndOpaque(myCbInLiterals);


    myCbMatchCase.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbMatchCase.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setCaseSensitive(b);
        FindSettings.getInstance().setLocalCaseSensitive(b);
      }
    });

    myCbWholeWords.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbWholeWords.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setWholeWordsOnly(b);
        FindSettings.getInstance().setLocalWholeWordsOnly(b);
      }
    });

    myCbRegexp.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbRegexp.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setRegularExpressions(b);
      }
    });

    myCbInComments.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbInComments.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setInCommentsOnly(b);
      }
    });

    myCbInLiterals.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbInLiterals.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setInStringLiteralsOnly(b);
      }
    });

    JPanel tailPanel = new NonOpaquePanel(new BorderLayout(5, 0));
    JPanel tailContainer = new NonOpaquePanel(new BorderLayout(5, 0));
    tailContainer.add(tailPanel, BorderLayout.EAST);
    add(tailContainer, BorderLayout.CENTER);

    myMatchInfoLabel = new JLabel();
    setSmallerFontAndOpaque(myMatchInfoLabel);

    myClickToHighlightLabel = new LinkLabel("Click to highlight", null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        setMatchesLimit(Integer.MAX_VALUE);
        updateResults(true);
      }
    });
    setSmallerFontAndOpaque(myClickToHighlightLabel);
    myClickToHighlightLabel.setVisible(false);

    JLabel closeLabel = new JLabel(" ", IconLoader.getIcon("/actions/cross.png"), SwingConstants.RIGHT);
    closeLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        close();
      }
    });

    closeLabel.setToolTipText("Close search bar (Escape)");

    JPanel labelsPanel = new NonOpaquePanel(new FlowLayout());

    labelsPanel.add(myMatchInfoLabel);
    labelsPanel.add(myClickToHighlightLabel);
    tailPanel.add(labelsPanel, BorderLayout.CENTER);
    tailPanel.add(closeLabel, BorderLayout.EAST);

    configureTextField(mySearchField);
    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        setMatchesLimit(MATCHES_LIMIT);
        String text = mySearchField.getText();
        if (!StringUtil.isEmpty(text)) {
          myFindModel.setStringToFind(text);
          updateResults(true);
        }
      }
    });

    setSmallerFont(mySearchField);
    mySearchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if ("".equals(mySearchField.getText())) {
          close();
        }
        else {
          requestFocus(myEditor.getContentComponent());
          addTextToRecents(EditorSearchComponent.this.mySearchField);
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK),
                                         JComponent.WHEN_FOCUSED);

    final String initialText = myFindModel.getStringToFind();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        setInitialText(initialText);
      }
    });

    new VariantsCompletionAction(mySearchField); // It registers a shortcut set automatically on construction
  }

  @Override
  public void findModelChanged(FindModel findModel) {
    syncFindModels(myFindModel, findModel);
  }

  public boolean isRegexp() {
    return myFindModel.isRegularExpressions();
  }

  public void setRegexp(boolean val) {
    myFindModel.setRegularExpressions(val);
  }

  public FindModel getFindModel() {
    return myFindModel;
  }

  private static void syncFindModels(FindModel to, FindModel from) {
    to.setCaseSensitive(from.isCaseSensitive());
    to.setWholeWordsOnly(from.isWholeWordsOnly());
    to.setRegularExpressions(from.isRegularExpressions());
    to.setInCommentsOnly(from.isInCommentsOnly());
    to.setInStringLiteralsOnly(from.isInStringLiteralsOnly());
  }

  private void updateFindModelWithUI() {
    myFindModel.setCaseSensitive(myCbMatchCase.isSelected());
    myFindModel.setWholeWordsOnly(myCbWholeWords.isSelected());
    myFindModel.setRegularExpressions(myCbRegexp.isSelected());
    myFindModel.setInCommentsOnly(myCbInComments.isSelected());
    myFindModel.setInStringLiteralsOnly(myCbInLiterals.isSelected());
    myFindModel.setFromCursor(false);
    myFindModel.setSearchHighlighters(true);

  }

  private void updateUIWithFindModel() {
    myCbMatchCase.setSelected(myFindModel.isCaseSensitive());
    myCbWholeWords.setSelected(myFindModel.isWholeWordsOnly());
    myCbWholeWords.setEnabled(!myFindModel.isRegularExpressions());
    myCbRegexp.setSelected(myFindModel.isRegularExpressions());
    myCbInComments.setSelected(myFindModel.isInCommentsOnly());
    myCbInLiterals.setSelected(myFindModel.isInStringLiteralsOnly());

    String stringToFind = myFindModel.getStringToFind();

    if (!StringUtil.equals(stringToFind, mySearchField.getText())) {
      mySearchField.setText(stringToFind);
    }

    myCbWholeWords.setEnabled(wholeWordsApplicable(stringToFind));

    setTrackingSelection(!myFindModel.isGlobal());

    if (myFindModel.isReplaceState() && myReplacementPane == null) {
      configureReplacementPane();
    } else if (!myFindModel.isReplaceState() && myReplacementPane != null) {
      remove(myReplacementPane);
      myReplacementPane = null;
    }
    if (myFindModel.isReplaceState()) {
      mySelectionOnly.setSelected(!myFindModel.isGlobal());
      myPreserveCase.setSelected(myFindModel.isPreserveCase());
      myPreserveCase.setEnabled(!myFindModel.isRegularExpressions());
      myPreserveCase.setEnabled(!myFindModel.isWholeWordsOnly());
      String stringToReplace = myFindModel.getStringToReplace();
      if (!StringUtil.equals(stringToReplace, myReplaceField.getText())) {
        myReplaceField.setText(stringToReplace);
      }
      updateExcludeStatus();
    }
  }

  private boolean wholeWordsApplicable(String stringToFind) {
    return !stringToFind.startsWith(" ") &&
           !stringToFind.startsWith("\t") &&
           !stringToFind.endsWith(" ") &&
           !stringToFind.endsWith("\t");
  }

  private static FindModel createFindModel(FindModel findInFileModel, boolean isReplace) {
    FindModel result = new FindModel();
    result.copyFrom(findInFileModel);
    if (isReplace) {
      result.setReplaceState(isReplace);
    }
    return result;
  }

  private void setMatchesLimit(int value) {
    mySearchResults.setMatchesLimit(value);
  }

  private void configureReplacementPane() {
    myReplacementPane = createLeadPane();
    myReplaceField = createTextField();
    configureTextField(myReplaceField);
    myReplaceField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        setMatchesLimit(MATCHES_LIMIT);
        myFindModel.setStringToReplace(myReplaceField.getText());
      }
    });
    myReplaceField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.performReplace();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    myReplaceField.setText(myFindModel.getStringToReplace());
    myReplacementPane.add(myReplaceField);
    add(myReplacementPane, BorderLayout.SOUTH);
    myPreserveCase = new JCheckBox("Preserve case");
    mySelectionOnly = new JCheckBox("Selection only");

    myPreserveCase.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        final boolean b = myPreserveCase.isSelected();
        myFindModel.setPreserveCase(b);
      }
    });
    myPreserveCase.setMnemonic('P');
    mySelectionOnly.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        final boolean b = mySelectionOnly.isSelected();
        myFindModel.setGlobal(!b);
      }
    });
    mySelectionOnly.setMnemonic('S');


    myReplaceButton = new JButton("Replace");
    myReplaceButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.performReplace();
      }
    });
    myReplaceButton.setMnemonic('p');

    myReplaceAllButton = new JButton("Replace all");
    myReplaceAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.performReplaceAll();
      }
    });
    myReplaceAllButton.setMnemonic('a');

    myExcludeButton = new JButton("");

    myExcludeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.exclude();
      }
    });
    myExcludeButton.setMnemonic('l');

    myReplacementPane.add(myReplaceButton);
    myReplacementPane.add(myReplaceAllButton);
    myReplacementPane.add(myExcludeButton);

    myOptionsPane.add(mySelectionOnly);
    myOptionsPane.add(myPreserveCase);

    setSmallerFontAndOpaque(myReplaceButton);
    setSmallerFontAndOpaque(myReplaceAllButton);
    setSmallerFontAndOpaque(myExcludeButton);
    
    setSmallerFontAndOpaque(mySelectionOnly);
    setSmallerFontAndOpaque(myPreserveCase);
    setSmallerFont(myReplaceField);
    myReplaceField.putClientProperty("AuxEditorComponent", Boolean.TRUE);
    new VariantsCompletionAction(myReplaceField);
  }

  private void updateExcludeStatus() {
    if (myExcludeButton != null) {
      LiveOccurrence cursor = mySearchResults.getCursor();
      myExcludeButton.setText(cursor == null || !mySearchResults.isExcluded(cursor) ? "Exclude" : "Include");
      myReplaceAllButton.setEnabled(mySearchResults.hasMatches());
      if (cursor != null) {
        myExcludeButton.setEnabled(true);
        myReplaceButton.setEnabled(true);
      } else {
        myExcludeButton.setEnabled(false);
        myReplaceButton.setEnabled(false);
      }
    }
  }

  private void updateModelWithSelectionMode() {
    final boolean b = mySelectionOnly.isSelected();
    myFindModel.setGlobal(!b);
  }

  private void setTrackingSelection(boolean b) {
    if (b) {
      if (!myListeningSelection) {
        myEditor.getSelectionModel().addSelectionListener(this);
      }
    } else {
      if (myListeningSelection) {
        myEditor.getSelectionModel().removeSelectionListener(this);
      }
    }
    myListeningSelection = b;
  }

  private NonOpaquePanel createLeadPane() {
    return new NonOpaquePanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
  }

  private JTextField createTextField() {
    return new JTextField() {
      protected void paintBorder(final Graphics g) {
        super.paintBorder(g);

        if (!(UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderQuaquaLookAndFeel()) && isFocusOwner()) {
          final Rectangle bounds = getBounds();
          g.setColor(FOCUS_CATCHER_COLOR);
          g.drawRect(0, 0, bounds.width - 1, bounds.height - 1);
        }
      }
    };
  }

  private void configureTextField(final JTextField searchField) {
    searchField.setColumns(25);

    searchField.addFocusListener(new FocusListener() {
      public void focusGained(final FocusEvent e) {
        searchField.repaint();
      }

      public void focusLost(final FocusEvent e) {
        searchField.repaint();
      }
    });



    searchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        close();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

    searchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (StringUtil.isEmpty(searchField.getText())) {
          showHistory(false, searchField);
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);

  }

  public void setInitialText(final String initialText) {
    final String text = initialText != null ? initialText : "";
    if (text.contains("\n")) {
      myFindModel.setRegularExpressions(true);
      setTextInField(StringUtil.escapeToRegexp(text));
    }
    else {
      setTextInField(text);
    }
    mySearchField.selectAll();
  }

  private void requestFocus(Component c) {
    IdeFocusManager.getInstance(myProject).requestFocus(c, true);
  }

  private void searchBackward() {
    moveCursor(SearchResults.Direction.UP);

    addTextToRecents(mySearchField);
  }

  private void searchForward() {
    moveCursor(SearchResults.Direction.DOWN);
    addTextToRecents(mySearchField);
  }

  private void addTextToRecents(JTextField textField) {
    final String text = textField.getText();
    if (text.length() > 0) {
      if (textField == mySearchField) {
        FindSettings.getInstance().addStringToFind(text);
      } else {
        FindSettings.getInstance().addStringToReplace(text);
      }
    }
  }

  @Override
  public void selectionChanged(SelectionEvent e) {
    updateResults(true);
  }

  public void moveCursor(SearchResults.Direction direction) {
    myLivePreviewController.moveCursor(direction, true);
  }

  public void replaceCurrent() {
    if (mySearchResults.getCursor() != null) {
      String replacement = myLivePreviewController.getStringToReplace(myEditor, mySearchResults.getCursor());
      myLivePreviewController.performReplace(mySearchResults.getCursor(), replacement, myEditor);
    }
  }

  private static void setSmallerFontAndOpaque(final JComponent component) {
    setSmallerFont(component);
    component.setOpaque(false);
  }

  private static void setSmallerFont(final JComponent component) {
    if (SystemInfo.isMac) {
      Font f = component.getFont();
      component.setFont(f.deriveFont(f.getStyle(), f.getSize() - 2));
    }
  }

  public void requestFocus() {
    requestFocus(mySearchField);
  }

  private void close() {
    if (myEditor.getSelectionModel().hasSelection()) {
      myEditor.getCaretModel().moveToOffset(myEditor.getSelectionModel().getSelectionStart());
      myEditor.getSelectionModel().removeSelection();
    }
    IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), false);
    FindManager.getInstance(myProject).getFindInFileModel().removeObserver(this);
    mySearchResults.dispose();
    myLivePreview.cleanUp();
    myEditor.setHeaderComponent(null);
    addTextToRecents(mySearchField);
  }

  @Override
  public void addNotify() {
    super.addNotify();

    myDocumentListener = new com.intellij.openapi.editor.event.DocumentAdapter() {
      public void documentChanged(final com.intellij.openapi.editor.event.DocumentEvent e) {
        updateResults(false);
      }
    };

    myEditor.getDocument().addDocumentListener(myDocumentListener);

    if (myLivePreview != null) {
      myLivePreviewController.updateInBackground(mySearchResults.getFindModel(), false);
    }
  }

  public void removeNotify() {
    super.removeNotify();

    if (myDocumentListener != null) {
      myEditor.getDocument().removeDocumentListener(myDocumentListener);
      myDocumentListener = null;
    }
    myLivePreview.cleanUp();
    if (myListeningSelection) {
      myEditor.getSelectionModel().removeSelectionListener(this);
    }
  }

  private void updateResults(final boolean allowedToChangedEditorSelection) {
    myMatchInfoLabel.setFont(myMatchInfoLabel.getFont().deriveFont(Font.PLAIN));
    final String text = mySearchField.getText();
    if (text.length() == 0) {
      nothingToSearchFor();
    }
    else {

      setRegularBackground();
      if (myFindModel.isRegularExpressions()) {
        try {
          Pattern.compile(text);
        }
        catch (Exception e) {
          setNotFoundBackground();
          myMatchInfoLabel.setText("Incorrect regular expression");
          boldMatchInfo();
          myClickToHighlightLabel.setVisible(false);
          return;
        }
      }


      final FindManager findManager = FindManager.getInstance(myProject);
      if (allowedToChangedEditorSelection) {
        findManager.setFindWasPerformed();
        FindModel copy = new FindModel();
        copy.copyFrom(myFindModel);
        copy.setReplaceState(false);
        findManager.setFindNextModel(copy);
      }
      
      myLivePreviewController.updateInBackground(myFindModel, allowedToChangedEditorSelection);
    }
  }

  private void nothingToSearchFor() {
    setRegularBackground();
    myMatchInfoLabel.setText("");
    myClickToHighlightLabel.setVisible(false);
    if (myLivePreview != null) {
      myLivePreview.cleanUp();
    }
  }

  private void boldMatchInfo() {
    myMatchInfoLabel.setFont(myMatchInfoLabel.getFont().deriveFont(Font.BOLD));
  }

  private void setRegularBackground() {
    mySearchField.setBackground(myDefaultBackground);
  }

  private void setNotFoundBackground() {
    mySearchField.setBackground(LightColors.RED);
  }

  public String getTextInField() {
    return mySearchField.getText();
  }

  public void setTextInField(final String text) {
    mySearchField.setText(text);
    if (!StringUtil.isEmpty(text)) {
      myFindModel.setStringToFind(text);
    }
  }

  private class PrevOccurrenceAction extends AnAction implements DumbAware {
    public PrevOccurrenceAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_OCCURENCE));

      ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
      ContainerUtil.addAll(shortcuts,ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS).getShortcutSet().getShortcuts());
      ContainerUtil.addAll(shortcuts,
                           ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP).getShortcutSet().getShortcuts());
      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), null));

      registerShortcutsForComponent(shortcuts, mySearchField, this);
      if (myFindModel.isReplaceState()) {
        registerShortcutsForComponent(shortcuts, myReplaceField, this);
      }
    }

    public void actionPerformed(final AnActionEvent e) {
      searchBackward();
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(hasMatches());
    }
  }

  public boolean hasMatches() {
    return myLivePreview != null && myLivePreview.hasMatches();
  }

  private static void registerShortcutsForComponent(ArrayList<Shortcut> shortcuts, JComponent component, AnAction a) {
    a.registerCustomShortcutSet(
      new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])),
      component);
  }

  private class NextOccurrenceAction extends AnAction implements DumbAware {
    public NextOccurrenceAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_OCCURENCE));
      ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
      ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT).getShortcutSet().getShortcuts());
      ContainerUtil
        .addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN).getShortcutSet().getShortcuts());
      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null));

      registerShortcutsForComponent(shortcuts, mySearchField, this);
      if (myFindModel.isReplaceState()) {
        registerShortcutsForComponent(shortcuts, myReplaceField, this);
      }
    }

    public void actionPerformed(final AnActionEvent e) {
      searchForward();
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(hasMatches());
    }
  }

  private class ShowHistoryAction extends AnAction implements DumbAware {
    private JTextField myTextField;

    private ShowHistoryAction(JTextField textField) {
      myTextField = textField;
      getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/search.png"));
      getTemplatePresentation().setDescription("Search history");
      getTemplatePresentation().setText("Search History");

      ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
      ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet().getShortcuts());
      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), null));
      ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction("IncrementalSearch").getShortcutSet().getShortcuts());

      registerCustomShortcutSet(
        new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])),
        myTextField);
    }

    public void actionPerformed(final AnActionEvent e) {
      showHistory(e.getInputEvent() instanceof MouseEvent, myTextField);
    }
  }

  private class FindAllAction extends AnAction implements DumbAware {
    private FindAllAction() {
      getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/export.png"));
      getTemplatePresentation().setDescription("Export matches to Find tool window");
      getTemplatePresentation().setText("Find All");
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).getShortcutSet(), mySearchField);
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(hasMatches() && PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument()) != null);
    }

    public void actionPerformed(final AnActionEvent e) {
      final FindModel model = FindManager.getInstance(myProject).getFindInFileModel();
      final FindModel realModel = (FindModel)model.clone();
      realModel.setStringToFind(getTextInField());
      FindUtil.findAll(myProject, myEditor, realModel);
    }
  }

  private void showHistory(final boolean byClickingToolbarButton, JTextField textField) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("find.recent.search");
    FindSettings settings = FindSettings.getInstance();
    String[] recents = textField == mySearchField ?  settings.getRecentFindStrings() : settings.getRecentReplaceStrings();
    showCompletionPopup(new JBList(ArrayUtil.reverseArray(recents)), "Recent Searches",
                        byClickingToolbarButton, textField);
  }

  private class VariantsCompletionAction extends AnAction {
    private JTextField myTextField;

    private VariantsCompletionAction(JTextField textField) {
      final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
      myTextField = textField;
      if (action != null) {
        registerCustomShortcutSet(action.getShortcutSet(), myTextField);
      }
    }

    public void actionPerformed(final AnActionEvent e) {
      final String prefix = getPrefix();
      if (prefix.length() == 0) return;

      final String[] array = calcWords(prefix);
      if (array.length == 0) {
        return;
      }

      FeatureUsageTracker.getInstance().triggerFeatureUsed("find.completion");
      final JList list = new JBList(array) {
        protected void paintComponent(final Graphics g) {
          UISettings.setupAntialiasing(g);
          super.paintComponent(g);
        }
      };
      list.setBackground(COMPLETION_BACKGROUND_COLOR);
      list.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

      showCompletionPopup(list, null, e.getInputEvent() instanceof MouseEvent, myTextField);
    }

    private String getPrefix() {
      return myTextField.getText().substring(0, myTextField.getCaret().getDot());
    }

    private String[] calcWords(final String prefix) {
      final NameUtil.Matcher matcher = NameUtil.buildMatcher(prefix, 0, true, true);
      final Set<String> words = new HashSet<String>();
      CharSequence chars = myEditor.getDocument().getCharsSequence();

      IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
        public void run(final CharSequence chars, final int start, final int end) {
          final String word = chars.subSequence(start, end).toString();
          if (matcher.matches(word)) {
            words.add(word);
          }
        }
      }, chars, 0, chars.length());


      ArrayList<String> sortedWords = new ArrayList<String>(words);
      Collections.sort(sortedWords);

      return ArrayUtil.toStringArray(sortedWords);
    }
  }

  private void showCompletionPopup(final JList list, String title, final boolean byClickingToolbarButton, final JTextField textField) {

    final Runnable callback = new Runnable() {
      public void run() {
        String selectedValue = (String)list.getSelectedValue();
        if (selectedValue != null) {
          textField.setText(selectedValue);
        }
      }
    };

    final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }

    final JBPopup popup = builder.setMovable(false).setResizable(false)
      .setRequestFocus(true).setItemChoosenCallback(callback).createPopup();

    if (byClickingToolbarButton) {
      popup.showUnderneathOf(myToolbarComponent);
    }
    else {
      popup.showUnderneathOf(textField);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    final Graphics2D g2d = (Graphics2D) g;

    g2d.setPaint(new GradientPaint(0, 0, GRADIENT_C1, 0, getHeight(), GRADIENT_C2));
    g2d.fillRect(1, 1, getWidth(), getHeight() - 1);
    
    g.setColor(BORDER_COLOR);
    g2d.setPaint(null);
    g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
  }

  private class MyLivePreviewController extends LivePreviewControllerBase {
    public MyLivePreviewController() {
      super(EditorSearchComponent.this.mySearchResults, EditorSearchComponent.this.myLivePreview);
    }

    @Override
    public void getFocusBack() {
      if (myFindModel != null && myFindModel.isReplaceState()) {
        requestFocus(myReplaceField);
      } else {
        requestFocus(mySearchField);
      }
    }

    public void performReplace() {
      String replacement = getStringToReplace(myEditor, mySearchResults.getCursor());
      performReplace(mySearchResults.getCursor(), replacement, myEditor);
      getFocusBack();
      addTextToRecents(myReplaceField);
    }

    public void exclude() {
      mySearchResults.exclude(mySearchResults.getCursor());
    }

    public void performReplaceAll() {
      performReplaceAll(myEditor);
    }
  }
}
