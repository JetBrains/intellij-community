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

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.regex.Pattern;

public class EditorSearchComponent extends JPanel implements DataProvider {
  private final JLabel myMatchInfoLabel;
  private final Project myProject;
  private final Editor myEditor;
  private final JTextField mySearchField;
  private final Color myDefaultBackground;

  private final Color GRADIENT_C1;
  private final Color GRADIENT_C2;
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);
  private static final Color COMPLETION_BACKGROUND_COLOR = new Color(235, 244, 254);
  private static final Color FOCUS_CATCHER_COLOR = new Color(0x9999ff);
  private final JComponent myToolbarComponent;
  private com.intellij.openapi.editor.event.DocumentAdapter myDocumentListener;
  private ArrayList<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();
  private boolean myOkToSearch = false;
  private boolean myHasMatches = false;
  private final JCheckBox myCbRegexp;
  private final JCheckBox myCbWholeWords;
  private final JCheckBox myCbInComments;
  private final JCheckBox myCbInLiterals;

  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (PlatformDataKeys.EDITOR_EVEN_IF_INACTIVE.is(dataId)) {
      return myEditor;
    }
    return null;
  }

  public EditorSearchComponent(final Editor editor, final Project project) {
    super(new BorderLayout(0, 0));

    GRADIENT_C1 = getBackground();
    GRADIENT_C2 = new Color(Math.max(0, GRADIENT_C1.getRed() - 0x18), Math.max(0, GRADIENT_C1.getGreen() - 0x18), Math.max(0, GRADIENT_C1.getBlue() - 0x18));
    
    myProject = project;
    myEditor = editor;

    JPanel leadPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    add(leadPanel, BorderLayout.WEST);

    mySearchField = new JTextField() {
      protected void paintBorder(final Graphics g) {
        super.paintBorder(g);

        final LafManager lafManager = LafManager.getInstance();
        if (!(lafManager.isUnderAquaLookAndFeel() || lafManager.isUnderQuaquaLookAndFeel()) && isFocusOwner()) {
          final Rectangle bounds = getBounds();
          g.setColor(FOCUS_CATCHER_COLOR);
          g.drawRect(0, 0, bounds.width - 1, bounds.height - 1);
        }
      }
    };

    mySearchField.addFocusListener(new FocusListener() {
      public void focusGained(final FocusEvent e) {
        mySearchField.repaint();
      }

      public void focusLost(final FocusEvent e) {
        mySearchField.repaint();
      }
    });

    mySearchField.putClientProperty("AuxEditorComponent", Boolean.TRUE);
    leadPanel.add(mySearchField);

    myDefaultBackground = mySearchField.getBackground();
    mySearchField.setColumns(25);

    setSmallerFont(mySearchField);

    DefaultActionGroup group = new DefaultActionGroup("search bar", false);
    group.add(new ShowHistoryAction());
    group.add(new PrevOccurenceAction());
    group.add(new NextOccurenceAction());
    group.add(new FindAllAction());

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("SearchBar", group, true);
    tb.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myToolbarComponent = tb.getComponent();
    myToolbarComponent.setBorder(null);
    myToolbarComponent.setOpaque(false);
    leadPanel.add(myToolbarComponent);

    final JCheckBox cbMatchCase = new NonFocusableCheckBox("Case sensitive");
    myCbWholeWords = new NonFocusableCheckBox("Match whole words only");
    myCbRegexp = new NonFocusableCheckBox("Regex");
    myCbInComments = new NonFocusableCheckBox("In comments");
    myCbInLiterals = new NonFocusableCheckBox("In literals");

    leadPanel.add(cbMatchCase);
    leadPanel.add(myCbWholeWords);
    leadPanel.add(myCbRegexp);
    if (FindManagerImpl.ourHasSearchInCommentsAndLiterals) {
      leadPanel.add(myCbInComments);
      leadPanel.add(myCbInLiterals);
    }

    cbMatchCase.setSelected(isCaseSensitive());
    myCbWholeWords.setSelected(isWholeWords());
    myCbRegexp.setSelected(isRegexp());
    myCbInComments.setSelected(isInComments());
    myCbInLiterals.setSelected(isInLiterals());

    cbMatchCase.setMnemonic('C');
    myCbWholeWords.setMnemonic('M');
    myCbRegexp.setMnemonic('R');
    myCbInComments.setMnemonic('o');
    myCbInLiterals.setMnemonic('l');

    setSmallerFontAndOpaque(myCbWholeWords);
    setSmallerFontAndOpaque(cbMatchCase);
    setSmallerFontAndOpaque(myCbRegexp);
    setSmallerFontAndOpaque(myCbInComments);
    setSmallerFontAndOpaque(myCbInLiterals);

    cbMatchCase.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = cbMatchCase.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setCaseSensitive(b);
        FindSettings.getInstance().setLocalCaseSensitive(b);
        updateResults(true);
      }
    });

    myCbWholeWords.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbWholeWords.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setWholeWordsOnly(b);
        FindSettings.getInstance().setLocalWholeWordsOnly(b);
        updateResults(true);
      }
    });

    myCbRegexp.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbRegexp.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setRegularExpressions(b);
        myCbWholeWords.setEnabled(!b);
        updateResults(true);
      }
    });

    myCbInComments.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbInComments.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setInCommentsOnly(b);
        updateResults(true);
      }
    });

    myCbInLiterals.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = myCbInLiterals.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setInStringLiteralsOnly(b);
        updateResults(true);
      }
    });

    JPanel tailPanel = new NonOpaquePanel(new BorderLayout(5, 0));
    JPanel tailContainer = new NonOpaquePanel(new BorderLayout(5, 0));
    tailContainer.add(tailPanel, BorderLayout.EAST);
    add(tailContainer, BorderLayout.CENTER);

    myMatchInfoLabel = new JLabel();
    setSmallerFontAndOpaque(myMatchInfoLabel);

    JLabel closeLabel = new JLabel(" ", IconLoader.getIcon("/actions/cross.png"), JLabel.RIGHT);
    closeLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        close();
      }
    });

    closeLabel.setToolTipText("Close search bar (Escape)");

    tailPanel.add(myMatchInfoLabel, BorderLayout.CENTER);
    tailPanel.add(closeLabel, BorderLayout.EAST);

    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateResults(true);
      }
    });

    mySearchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        close();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

    mySearchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (getTextInField().length() == 0) {
          showHistory(false);
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);

    mySearchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if ("".equals(mySearchField.getText())) {
          close();
        }
        else {
          requestFocus(myEditor.getContentComponent());
          addCurrentTextToRecents();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_FOCUSED);

    final String initialText = myEditor.getSelectionModel().getSelectedText();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        setInitialText(initialText);
      }
    });

    new VariantsCompletionAction(); // It registers a shortcut set automatically on construction
  }

  public void setInitialText(final String initialText) {
    final String text = initialText != null ? initialText : "";
    if (text.contains("\n")) {
      setRegexp(true);
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
    if (hasMatches()) {
      final SelectionModel model = myEditor.getSelectionModel();
      if (model.hasSelection()) {
        if (Comparing.equal(mySearchField.getText(), model.getSelectedText(), isCaseSensitive()) && myEditor.getCaretModel().getOffset() == model.getSelectionEnd()) {
          myEditor.getCaretModel().moveToOffset(model.getSelectionStart());
        }
      }

      FindUtil.searchBack(myProject, myEditor);
      addCurrentTextToRecents();
    }
  }

  private void searchForward() {
    if (hasMatches()) {
      final SelectionModel model = myEditor.getSelectionModel();
      if (model.hasSelection()) {
        if (Comparing.equal(mySearchField.getText(), model.getSelectedText(), isCaseSensitive()) && myEditor.getCaretModel().getOffset() == model.getSelectionStart()) {
          myEditor.getCaretModel().moveToOffset(model.getSelectionEnd());
        }
      }

      FindUtil.searchAgain(myProject, myEditor);
      addCurrentTextToRecents();
    }
  }

  private void addCurrentTextToRecents() {
    final String text = mySearchField.getText();
    if (text.length() > 0) {
      FindSettings.getInstance().addStringToFind(text);
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
    removeCurrentHighlights();
    if (myEditor.getSelectionModel().hasSelection()) {
      myEditor.getCaretModel().moveToOffset(myEditor.getSelectionModel().getSelectionStart());
      myEditor.getSelectionModel().removeSelection();
    }
    IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), false);

    myEditor.setHeaderComponent(null);
    addCurrentTextToRecents();
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
  }

  public void removeNotify() {
    super.removeNotify();

    if (myDocumentListener != null) {
      myEditor.getDocument().removeDocumentListener(myDocumentListener);
      myDocumentListener = null;
    }
  }

  private void updateResults(boolean allowedToChangedEditorSelection) {
    removeCurrentHighlights();
    myMatchInfoLabel.setFont(myMatchInfoLabel.getFont().deriveFont(Font.PLAIN));
    String text = mySearchField.getText();
    if (text.length() == 0) {
      setRegularBackground();
      myMatchInfoLabel.setText("");
      myOkToSearch = false;
    }
    else {
      myOkToSearch = true;
      FindManager findManager = FindManager.getInstance(myProject);
      FindModel model = new FindModel();
      model.setCaseSensitive(isCaseSensitive());
      model.setInCommentsOnly(isInComments());
      model.setInStringLiteralsOnly(isInLiterals());

      if (isRegexp()) {
        model.setWholeWordsOnly(false);
        model.setRegularExpressions(true);
        try {
          Pattern.compile(text);
        }
        catch (Exception e) {
          myOkToSearch = false;
          setNotFoundBackground();
          myMatchInfoLabel.setText("Incorrect regular expression");
          boldMatchInfo();
          return;
        }
      }
      else {
        model.setWholeWordsOnly(isWholeWords());
        model.setRegularExpressions(false);
      }

      model.setFromCursor(false);
      model.setStringToFind(text);
      model.setSearchHighlighters(true);
      int offset = 0;
      VirtualFile virtualFile = FindUtil.getVirtualFile(myEditor);
      ArrayList<FindResult> results = new ArrayList<FindResult>();

      while (true) {
        FindResult result = findManager.findString(myEditor.getDocument().getCharsSequence(), offset, model, virtualFile);
        if (!result.isStringFound()) break;
        offset = result.getEndOffset();
        results.add(result);

        if (results.size() > 100) break;
      }

      if (allowedToChangedEditorSelection) {
        int currentOffset = myEditor.getCaretModel().getOffset();
        if (myEditor.getSelectionModel().hasSelection()) {
          currentOffset = Math.min(currentOffset, myEditor.getSelectionModel().getSelectionStart());
        }

        if (!findAndSelectFirstUsage(findManager, model, currentOffset, virtualFile)) {
          findAndSelectFirstUsage(findManager, model, 0, virtualFile);
        }
      }

      final int count = results.size();
      if (count <= 100) {
        highlightResults(text, results);

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
        myMatchInfoLabel.setText("More than 100 matches");
        boldMatchInfo();
      }

      if (allowedToChangedEditorSelection) {
        findManager.setFindWasPerformed();
        findManager.setFindNextModel(model);
      }
    }
  }

  private void boldMatchInfo() {
    myMatchInfoLabel.setFont(myMatchInfoLabel.getFont().deriveFont(Font.BOLD));
  }

  private void setRegularBackground() {
    myHasMatches = true;
    mySearchField.setBackground(myDefaultBackground);
  }

  private void setNotFoundBackground() {
    myHasMatches = false;
    mySearchField.setBackground(LightColors.RED);
  }

  public String getTextInField() {
    return mySearchField.getText();
  }

  private boolean isWholeWords() {
    return FindManager.getInstance(myProject).getFindInFileModel().isWholeWordsOnly();
  }

  private boolean isInComments() {
    return FindManager.getInstance(myProject).getFindInFileModel().isInCommentsOnly();
  }

  private boolean isInLiterals() {
    return FindManager.getInstance(myProject).getFindInFileModel().isInStringLiteralsOnly();
  }

  private boolean isCaseSensitive() {
    return FindManager.getInstance(myProject).getFindInFileModel().isCaseSensitive();
  }

  public boolean isRegexp() {
    return myCbRegexp.isSelected() || FindManager.getInstance(myProject).getFindInFileModel().isRegularExpressions();
  }

  public void setRegexp(boolean r) {
    myCbRegexp.setSelected(r);
    myCbWholeWords.setEnabled(!r);
    updateResults(false);
  }

  private void removeCurrentHighlights() {
    final HighlightManagerImpl highlightManager = (HighlightManagerImpl)HighlightManager.getInstance(myProject);
    for (RangeHighlighter highlighter : myHighlighters) {
      highlightManager.removeSegmentHighlighter(myEditor, highlighter);
    }
  }

  private boolean findAndSelectFirstUsage(final FindManager findManager, final FindModel model, final int offset, VirtualFile file) {
    final FindResult firstResult = findManager.findString(myEditor.getDocument().getCharsSequence(), offset, model, file);
    if (firstResult.isStringFound()) {
      myEditor.getSelectionModel().setSelection(firstResult.getStartOffset(), firstResult.getEndOffset());

      myEditor.getCaretModel().moveToOffset(firstResult.getEndOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

      myEditor.getCaretModel().moveToOffset(firstResult.getStartOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return true;
    }

    return false;
  }

  private void highlightResults(final String text, final ArrayList<FindResult> results) {
    HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);

    myHighlighters = new ArrayList<RangeHighlighter>();
    for (FindResult result : results) {
      highlightManager.addRangeHighlight(myEditor, result.getStartOffset(), result.getEndOffset(), attributes, false, myHighlighters);
    }

    final String escapedText = StringUtil.escapeXml(text);
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.setErrorStripeTooltip(escapedText);
    }
  }

  public void setTextInField(final String text) {
    mySearchField.setText(text);
    updateResults(true);
  }

  private class PrevOccurenceAction extends AnAction implements DumbAware {
    public PrevOccurenceAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_OCCURENCE));

      ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
      shortcuts.addAll(Arrays.asList(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS).getShortcutSet().getShortcuts()));
      shortcuts.addAll(Arrays.asList(ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP).getShortcutSet().getShortcuts()));
      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), null));

      registerCustomShortcutSet(
        new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])),
        mySearchField);
    }

    public void actionPerformed(final AnActionEvent e) {
      searchBackward();
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(hasMatches());
    }
  }

  public boolean hasMatches() {
    return myOkToSearch && myHasMatches;
  }

  private class NextOccurenceAction extends AnAction implements DumbAware {
    public NextOccurenceAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_OCCURENCE));
      ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
      shortcuts.addAll(Arrays.asList(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT).getShortcutSet().getShortcuts()));
      shortcuts.addAll(Arrays.asList(ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN).getShortcutSet().getShortcuts()));
      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null));

      registerCustomShortcutSet(
        new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])),
        mySearchField);
    }

    public void actionPerformed(final AnActionEvent e) {
      searchForward();
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(hasMatches());
    }
  }

  private class ShowHistoryAction extends AnAction implements DumbAware {
    private ShowHistoryAction() {
      getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/search.png"));
      getTemplatePresentation().setDescription("Search history");
      getTemplatePresentation().setText("Search History");

      ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
      shortcuts.addAll(Arrays.asList(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet().getShortcuts()));
      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK), null));
      shortcuts.addAll(Arrays.asList(ActionManager.getInstance().getAction("IncrementalSearch").getShortcutSet().getShortcuts()));

      registerCustomShortcutSet(
        new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])),
        mySearchField);
    }

    public void actionPerformed(final AnActionEvent e) {
      showHistory(e.getInputEvent() instanceof MouseEvent);
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

  private void showHistory(final boolean byClickingToolbarButton) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("find.recent.search");
    showCompletionPopup(new JBList(ArrayUtil.reverseArray(FindSettings.getInstance().getRecentFindStrings())), "Recent Searches",
                        byClickingToolbarButton);
  }

  private class VariantsCompletionAction extends AnAction {
    private VariantsCompletionAction() {
      final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
      if (action != null) {
        registerCustomShortcutSet(action.getShortcutSet(), mySearchField);
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

      showCompletionPopup(list, null, e.getInputEvent() instanceof MouseEvent);
    }

    private String getPrefix() {
      return mySearchField.getText().substring(0, mySearchField.getCaret().getDot());
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

  private void showCompletionPopup(final JList list, String title, final boolean byClickingToolbarButton) {

    final Runnable callback = new Runnable() {
      public void run() {
        String selectedValue = (String)list.getSelectedValue();
        if (selectedValue != null) {
          mySearchField.setText(selectedValue);
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
      popup.showUnderneathOf(mySearchField);
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
}
