/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.find.impl;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FindDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.FindDialog");

  private ComboBox myInputComboBox;
  private ComboBox myReplaceComboBox;
  private StateRestoringCheckBox myCbCaseSensitive;
  private StateRestoringCheckBox myCbPreserveCase;
  private StateRestoringCheckBox myCbWholeWordsOnly;
  private ComboBox mySearchContext;
  private StateRestoringCheckBox myCbRegularExpressions;
  private JRadioButton myRbGlobal;
  private JRadioButton myRbSelectedText;
  private JRadioButton myRbForward;
  private JRadioButton myRbBackward;
  private JRadioButton myRbFromCursor;
  private JRadioButton myRbEntireScope;
  private JRadioButton myRbProject;
  private JRadioButton myRbDirectory;
  private JRadioButton myRbModule;
  private ComboBox myModuleComboBox;
  private ComboBox myDirectoryComboBox;
  private StateRestoringCheckBox myCbWithSubdirectories;
  private JCheckBox myCbToOpenInNewTab;
  private FindModel myModel;
  private Consumer<FindModel> myOkHandler;
  private FixedSizeButton mySelectDirectoryButton;
  private StateRestoringCheckBox myUseFileFilter;
  private ComboBox myFileFilter;
  private JCheckBox myCbToSkipResultsWhenOneUsage;
  private final Project myProject;
  private final Map<EditorTextField, DocumentAdapter> myComboBoxListeners = new HashMap<EditorTextField, DocumentAdapter>();

  private Action myFindAllAction;
  private JRadioButton myRbCustomScope;
  private ScopeChooserCombo myScopeCombo;
  protected JLabel myReplacePrompt;
  private HideableTitledPanel myScopePanel;
  private static boolean myPreviousResultsExpandedState;
  private static boolean myPreviewResultsTabWasSelected;
  private static final int RESULTS_PREVIEW_TAB_INDEX = 1;

  private Splitter myPreviewSplitter;
  private JBTable myResultsPreviewTable;
  private UsagePreviewPanel myUsagePreviewPanel;
  private TabbedPane myContent;
  private Alarm mySearchRescheduleOnCancellationsAlarm;
  private static final String PREVIEW_TITLE = UIBundle.message("tool.window.name.preview");
  private volatile ProgressIndicatorBase myResultsPreviewSearchProgress;

  public FindDialog(@NotNull Project project, @NotNull FindModel model, @NotNull Consumer<FindModel> myOkHandler){
    super(project, true);
    myProject = project;
    myModel = model;

    this.myOkHandler = myOkHandler;

    updateTitle();
    setOKButtonText(FindBundle.message("find.button"));
    init();
    initByModel();
    updateReplaceVisibility();

    if (haveResultsPreview()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          scheduleResultsUpdate();
        }
      }, ModalityState.any());
    }
  }

  private void updateTitle() {
    if (myModel.isReplaceState()){
      if (myModel.isMultipleFiles()){
        setTitle(FindBundle.message("find.replace.in.project.dialog.title"));
      }
      else{
        setTitle(FindBundle.message("find.replace.text.dialog.title"));
      }
    }
    else{
      setButtonsMargin(null);
      if (myModel.isMultipleFiles()){
        setTitle(FindBundle.message("find.in.path.dialog.title"));
      }
      else{
        setTitle(FindBundle.message("find.text.dialog.title"));
      }
    }
  }

  @Override
  public void doCancelAction() { // doCancel disposes fields and then calls dispose
    rememberResultsPreviewWasOpen();
    super.doCancelAction();
  }

  private void rememberResultsPreviewWasOpen() {
    if (myResultsPreviewTable != null) {
      int selectedIndex = myContent.getSelectedIndex();
      if (selectedIndex != -1) myPreviewResultsTabWasSelected = selectedIndex == RESULTS_PREVIEW_TAB_INDEX;
    }
  }

  @Override
  protected void dispose() {
    finishPreviousPreviewSearch();
    if (mySearchRescheduleOnCancellationsAlarm != null) Disposer.dispose(mySearchRescheduleOnCancellationsAlarm);
    if (myUsagePreviewPanel != null) Disposer.dispose(myUsagePreviewPanel);
    for(Map.Entry<EditorTextField, DocumentAdapter> e: myComboBoxListeners.entrySet()) {
      e.getKey().removeDocumentListener(e.getValue());
    }
    myComboBoxListeners.clear();
    if (myScopePanel != null) myPreviousResultsExpandedState = myScopePanel.isExpanded();
    rememberResultsPreviewWasOpen();
    super.dispose();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myInputComboBox;
  }

  @Override
  protected String getDimensionServiceKey() {
    return myModel.isReplaceState() ? "replaceTextDialog" : "findTextDialog";
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    if (!myModel.isMultipleFiles() && !myModel.isReplaceState() && myModel.isFindAllEnabled()) {
      return new Action[] { getFindAllAction(), getOKAction(), getCancelAction(), getHelpAction() };
    }
    return new Action[] { getOKAction(), getCancelAction(), getHelpAction() };
  }

  @NotNull
  private Action getFindAllAction() {
    return myFindAllAction = new AbstractAction(FindBundle.message("find.all.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        doOKAction(true);
      }
    };
  }

  @Override
  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JLabel prompt = new JLabel(FindBundle.message("find.text.to.find.label"));
    panel.add(prompt, new GridBagConstraints(0,0,1,1,0,1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,UIUtil.DEFAULT_VGAP,UIUtil.DEFAULT_HGAP), 0,0));

    myInputComboBox = new ComboBox(300);
    revealWhitespaces(myInputComboBox);
    initCombobox(myInputComboBox);

    myReplaceComboBox = new ComboBox(300);
    revealWhitespaces(myReplaceComboBox);

    initCombobox(myReplaceComboBox);
    final Component editorComponent = myReplaceComboBox.getEditor().getEditorComponent();
    editorComponent.addFocusListener(
      new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          myReplaceComboBox.getEditor().selectAll();
          editorComponent.removeFocusListener(this);
        }
      }
    );


    panel.add(myInputComboBox, new GridBagConstraints(1,0,1,1,1,1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,UIUtil.DEFAULT_VGAP,0), 0,0));
    prompt.setLabelFor(myInputComboBox.getEditor().getEditorComponent());

    myReplacePrompt = new JLabel(FindBundle.message("find.replace.with.label"));
    panel.add(myReplacePrompt, new GridBagConstraints(0,1,1,1,0,1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,UIUtil.DEFAULT_VGAP,UIUtil.DEFAULT_HGAP), 0,0));

    panel.add(myReplaceComboBox, new GridBagConstraints(1, 1, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                        new Insets(0, 0, UIUtil.DEFAULT_VGAP, 0), 0, 0));
    myReplacePrompt.setLabelFor(myReplaceComboBox.getEditor().getEditorComponent());
    return panel;
  }

  private void updateReplaceVisibility() {
    myReplacePrompt.setVisible(myModel.isReplaceState());
    myReplaceComboBox.setVisible(myModel.isReplaceState());
    if (myCbToSkipResultsWhenOneUsage != null) {
      myCbToSkipResultsWhenOneUsage.setVisible(!myModel.isReplaceState());
    }
    myCbPreserveCase.setVisible(myModel.isReplaceState());
  }

  private void revealWhitespaces(@NotNull ComboBox comboBox) {
    ComboBoxEditor comboBoxEditor = new RevealingSpaceComboboxEditor(myProject, comboBox);
    comboBox.setEditor(comboBoxEditor);
    comboBox.setRenderer(new EditorComboBoxRenderer(comboBoxEditor));
  }

  private void initCombobox(@NotNull final ComboBox comboBox) {
    comboBox.setEditable(true);
    comboBox.setMaximumRowCount(8);

    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateFindButton();
      }
    });

    Component editorComponent = comboBox.getEditor().getEditorComponent();

    if (editorComponent instanceof EditorTextField) {
      final EditorTextField etf = (EditorTextField) editorComponent;

      DocumentAdapter listener = new DocumentAdapter() {
        @Override
        public void documentChanged(final DocumentEvent e) {
          handleComboBoxValueChanged(comboBox);
        }
      };
      etf.addDocumentListener(listener);
      myComboBoxListeners.put(etf, listener);
    }
    else {
      editorComponent.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyReleased(KeyEvent e) {
            handleComboBoxValueChanged(comboBox);
          }
        }
      );
    }

    if (!myModel.isReplaceState()) {
      makeResultsPreviewActionOverride(
        comboBox,
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
        "choosePrevious",
        new Runnable() {
          @Override
          public void run() {
            int row = myResultsPreviewTable.getSelectedRow();
            if (row > 0) myResultsPreviewTable.setRowSelectionInterval(row - 1, row - 1);
          }
        }
      );

      makeResultsPreviewActionOverride(
        comboBox,
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
        "chooseNext",
        new Runnable() {
          @Override
          public void run() {
            int row = myResultsPreviewTable.getSelectedRow();
            if (row >= 0 && row + 1 < myResultsPreviewTable.getRowCount()) {
              myResultsPreviewTable.setRowSelectionInterval(row + 1, row + 1);
            }
          }
        }
      );

      new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (myResultsPreviewTable != null &&
              myContent.getSelectedIndex() == RESULTS_PREVIEW_TAB_INDEX) {
            navigateToSelectedUsage(myResultsPreviewTable);
          }
        }
      }.registerCustomShortcutSet(CommonShortcuts.getEditSource(), comboBox);
    }
  }

  private void makeResultsPreviewActionOverride(final JComboBox component, KeyStroke keyStroke, String newActionKey, final Runnable newAction) {
    InputMap inputMap = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    Object action = inputMap.get(keyStroke);
    inputMap.put(keyStroke, newActionKey);
    final Action previousAction = action != null ? component.getActionMap().get(action) : null;
    component.getActionMap().put(newActionKey, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (previousAction != null && component.isPopupVisible()) {
          previousAction.actionPerformed(e);
          return;
        }

        if(myResultsPreviewTable != null &&
          myContent.getSelectedIndex() == RESULTS_PREVIEW_TAB_INDEX) {
          newAction.run();
        }
      }
    });
  }

  private void handleComboBoxValueChanged(@NotNull ComboBox comboBox) {
    Object item = comboBox.getEditor().getItem();
    if (item != null && !item.equals(comboBox.getSelectedItem())){
      int caretPosition = getCaretPosition(comboBox);
      comboBox.setSelectedItem(item);
      setCaretPosition(comboBox, caretPosition);
    }

    scheduleResultsUpdate();
    validateFindButton();
  }

  private void findSettingsChanged() {
    if (haveResultsPreview()) {
      final ModalityState state = ModalityState.current();
      if (state == ModalityState.NON_MODAL) return; // skip initial changes

      finishPreviousPreviewSearch();
      mySearchRescheduleOnCancellationsAlarm.cancelAllRequests();
      final DefaultTableModel model = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };

      model.addColumn("Usages");

      final FindModel modelClone = myModel.clone();
      applyTo(modelClone, false);

      ValidationInfo result = getValidationInfo(modelClone);

      final PsiDirectory psiDirectory = FindInProjectUtil.getPsiDirectory(modelClone, myProject);

      final ProgressIndicatorBase progressIndicatorWhenSearchStarted = new ProgressIndicatorBase();
      myResultsPreviewSearchProgress = progressIndicatorWhenSearchStarted;

      myResultsPreviewTable.setModel(model);

      if (result != null) {
        myResultsPreviewTable.getEmptyText().setText(UIBundle.message("message.nothingToShow"));
        myContent.setTitleAt(RESULTS_PREVIEW_TAB_INDEX, PREVIEW_TITLE);
        return;
      }

      myResultsPreviewTable.getColumnModel().getColumn(0).setCellRenderer(new UsageTableCellRenderer());

      myResultsPreviewTable.getEmptyText().setText("Searching...");
      myContent.setTitleAt(RESULTS_PREVIEW_TAB_INDEX, PREVIEW_TITLE);

      final AtomicInteger resultsCount = new AtomicInteger();

      ProgressIndicatorUtils.scheduleWithWriteActionPriority(myResultsPreviewSearchProgress, new ReadTask() {
        @Override
        public void computeInReadAction(@NotNull ProgressIndicator indicator) {
          final UsageViewPresentation presentation =
            FindInProjectUtil.setupViewPresentation(FindSettings.getInstance().isShowResultsInSeparateView(), modelClone);
          final boolean showPanelIfOnlyOneUsage = !FindSettings.getInstance().isSkipResultsWithOneUsage();

          final FindUsagesProcessPresentation processPresentation =
            FindInProjectUtil.setupProcessPresentation(myProject, showPanelIfOnlyOneUsage, presentation);

          FindInProjectUtil.findUsages(modelClone, psiDirectory, myProject, new Processor<UsageInfo>() {
            @Override
            public boolean process(final UsageInfo info) {
              final Usage usage = UsageInfo2UsageAdapter.CONVERTER.fun(info);
              usage.getPresentation().getIcon(); // cache icon
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  model.addRow(new Object[]{usage});
                  if (model.getRowCount() == 1 && myResultsPreviewTable.getModel() == model) {
                    myResultsPreviewTable.setRowSelectionInterval(0, 0);
                  }
                }
              }, state);
              return resultsCount.incrementAndGet() < ShowUsagesAction.USAGES_PAGE_SIZE;
            }
          }, processPresentation);
          boolean succeeded = !progressIndicatorWhenSearchStarted.isCanceled();
          if (succeeded) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (progressIndicatorWhenSearchStarted == myResultsPreviewSearchProgress && !myResultsPreviewSearchProgress.isCanceled()) {
                  int occurrences = resultsCount.get();
                  if (occurrences == 0) myResultsPreviewTable.getEmptyText().setText(UIBundle.message("message.nothingToShow"));
                  myContent.setTitleAt(RESULTS_PREVIEW_TAB_INDEX, PREVIEW_TITLE + " (" + (occurrences != ShowUsagesAction.USAGES_PAGE_SIZE ? Integer.valueOf(occurrences): occurrences + "+") +")");
                }
              }
            }, state);
          }
        }

        @Override
        public void onCanceled(@NotNull ProgressIndicator indicator) {
          if (isShowing() && progressIndicatorWhenSearchStarted == myResultsPreviewSearchProgress) {
            scheduleResultsUpdate();
          }
        }
      });
    }
  }

  private void scheduleResultsUpdate() {
    if (mySearchRescheduleOnCancellationsAlarm == null) return;
    mySearchRescheduleOnCancellationsAlarm.cancelAllRequests();
    mySearchRescheduleOnCancellationsAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        findSettingsChanged();
      }
    }, 100);
  }

  private void finishPreviousPreviewSearch() {
    if (myResultsPreviewSearchProgress != null && !myResultsPreviewSearchProgress.isCanceled()) {
      myResultsPreviewSearchProgress.cancel();
    }
  }

  @NotNull
  public FindModel getModel() {
    return myModel;
  }

  public void setOkHandler(@NotNull Consumer<FindModel> okHandler) {
    myOkHandler = okHandler;
  }

  public void setModel(@NotNull FindModel model) {
    myModel = model;
    updateReplaceVisibility();
    updateTitle();
  }

  private static int getCaretPosition(@NotNull JComboBox comboBox) {
    Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField){
      JTextField textField = (JTextField)editorComponent;
      return textField.getCaretPosition();
    }
    return 0;
  }

  private static void setCaretPosition(@NotNull JComboBox comboBox, int position) {
    Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField){
      JTextField textField = (JTextField)editorComponent;
      textField.setCaretPosition(position);
    }
  }

  private void validateFindButton() {
    boolean okStatus = canSearchThisString() ||
                       myRbDirectory != null && myRbDirectory.isSelected() && StringUtil.isEmpty(getDirectory());
    setOKStatus(okStatus);
  }

  private boolean canSearchThisString() {
    return !StringUtil.isEmpty(getStringToFind()) || !myModel.isReplaceState() && !myModel.isFindAllEnabled() && getFileTypeMask() != null;
  }

  private void setOKStatus(boolean value) {
    setOKActionEnabled(value);
    if (myFindAllAction != null) {
      myFindAllAction.setEnabled(value);
    }
  }

  @Override
  public JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setLayout(new GridBagLayout());

    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;

    JPanel topOptionsPanel = new JPanel();
    topOptionsPanel.setLayout(new GridLayout(1, 2, UIUtil.DEFAULT_HGAP, 0));
    topOptionsPanel.add(createFindOptionsPanel());
    optionsPanel.add(topOptionsPanel, gbConstraints);
    
    JPanel resultsOptionPanel = null;
    
    if (myModel.isMultipleFiles()) {
      optionsPanel.add(createGlobalScopePanel(), gbConstraints);
      gbConstraints.weightx = 1;
      gbConstraints.weighty = 1;
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;

      gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      optionsPanel.add(createFilterPanel(),gbConstraints);

      myCbToSkipResultsWhenOneUsage = createCheckbox(FindSettings.getInstance().isSkipResultsWithOneUsage(), FindBundle.message("find.options.skip.results.tab.with.one.occurrence.checkbox"));
      resultsOptionPanel = createResultsOptionPanel(optionsPanel, gbConstraints);
      resultsOptionPanel.add(myCbToSkipResultsWhenOneUsage);

      myCbToSkipResultsWhenOneUsage.setVisible(!myModel.isReplaceState());

      if (haveResultsPreview()) {
        final JBTable table = new JBTable() {
          @Override
          public Dimension getPreferredSize() {
            return new Dimension(myInputComboBox.getWidth(), super.getPreferredSize().height);
          }
        };
        table.setShowColumns(false);
        table.setShowGrid(false);
        new NavigateToSourceListener().installOn(table);

        Splitter previewSplitter = new Splitter(true, 0.5f, 0.1f, 0.9f);
        myUsagePreviewPanel = new UsagePreviewPanel(myProject, new UsageViewPresentation());
        myResultsPreviewTable = table;
        myResultsPreviewTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
          @Override
          public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) return;
            int index = myResultsPreviewTable.getSelectionModel().getLeadSelectionIndex();
            if (index != -1) {
              UsageInfo usageInfo = ((UsageInfo2UsageAdapter)myResultsPreviewTable.getModel().getValueAt(index, 0)).getUsageInfo();
              myUsagePreviewPanel.updateLayout(Collections.singletonList(usageInfo));
            }
            else {
              myUsagePreviewPanel.updateLayout(null);
            }
          }
        });
        mySearchRescheduleOnCancellationsAlarm = new Alarm();
        previewSplitter.setFirstComponent(new JBScrollPane(myResultsPreviewTable));
        previewSplitter.setSecondComponent(myUsagePreviewPanel.createComponent());
        myPreviewSplitter = previewSplitter;
      }
    }
    else {
      JPanel leftOptionsPanel = new JPanel();
      leftOptionsPanel.setLayout(new GridLayout(3, 1, 0, 4));

      leftOptionsPanel.add(createDirectionPanel());
      leftOptionsPanel.add(createOriginPanel());
      leftOptionsPanel.add(createScopePanel());
      topOptionsPanel.add(leftOptionsPanel);
    }

    if (myModel.isOpenInNewTabVisible()){
      myCbToOpenInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
      myCbToOpenInNewTab.setFocusable(false);
      myCbToOpenInNewTab.setSelected(myModel.isOpenInNewTab());
      myCbToOpenInNewTab.setEnabled(myModel.isOpenInNewTabEnabled());

      if (resultsOptionPanel == null) resultsOptionPanel = createResultsOptionPanel(optionsPanel, gbConstraints);
      resultsOptionPanel.add(myCbToOpenInNewTab);
    }

    if (myPreviewSplitter != null) {
      TabbedPane pane = new JBTabsPaneImpl(myProject, SwingConstants.TOP, myDisposable);
      pane.insertTab("Options", null, optionsPanel, null, 0);
      pane.insertTab(PREVIEW_TITLE, null, myPreviewSplitter, null, RESULTS_PREVIEW_TAB_INDEX);
      myContent = pane;
      AnAction anAction = new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          int selectedIndex = myContent.getSelectedIndex();
          myContent.setSelectedIndex(1 - selectedIndex);
        }
      };

      final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_SWITCHER).getShortcutSet();
      anAction.registerCustomShortcutSet(shortcutSet, getRootPane());
      if (myPreviewResultsTabWasSelected) myContent.setSelectedIndex(RESULTS_PREVIEW_TAB_INDEX);

      return pane.getComponent();
    }

    return optionsPanel;
  }

  private boolean haveResultsPreview() {
    return Registry.is("ide.find.show.preview") && myModel.isMultipleFiles();
  }

  private JPanel createResultsOptionPanel(JPanel optionsPanel, GridBagConstraints gbConstraints) {
    JPanel resultsOptionPanel = new JPanel();
    resultsOptionPanel.setLayout(new BoxLayout(resultsOptionPanel, BoxLayout.Y_AXIS));

    myScopePanel = new HideableTitledPanel(FindBundle.message("results.options.group"), resultsOptionPanel,
                                           myPreviousResultsExpandedState);
    optionsPanel.add(myScopePanel, gbConstraints);
    return resultsOptionPanel;
  }

  @NotNull
  private JComponent createFilterPanel() {
    JPanel filterPanel = new JPanel();
    filterPanel.setLayout(new BorderLayout());
    filterPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.filter.file.name.group"),
                                                              true));

    myFileFilter = new ComboBox(100);
    initCombobox(myFileFilter);
    filterPanel.add(myUseFileFilter = createCheckbox(FindBundle.message("find.filter.file.mask.checkbox")),BorderLayout.WEST);
    filterPanel.add(myFileFilter,BorderLayout.CENTER);
    initFileFilter(myFileFilter, myUseFileFilter);
    myUseFileFilter.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        scheduleResultsUpdate();
        validateFindButton();
      }
    });
    return filterPanel;
  }

  public static void initFileFilter(@NotNull final JComboBox fileFilter, @NotNull final JCheckBox useFileFilter) {
    fileFilter.setEditable(true);
    String[] fileMasks = FindSettings.getInstance().getRecentFileMasks();
    for(int i=fileMasks.length-1; i >= 0; i--) {
      fileFilter.addItem(fileMasks[i]);
    }
    fileFilter.setEnabled(false);

    useFileFilter.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (useFileFilter.isSelected()) {
            fileFilter.setEnabled(true);
            fileFilter.getEditor().selectAll();
            fileFilter.getEditor().getEditorComponent().requestFocusInWindow();
          }
          else {
            fileFilter.setEnabled(false);
          }
        }
      }
    );
  }

  @Override
  public void doOKAction() {
    doOKAction(false);
  }

  private void doOKAction(boolean findAll) {
    FindModel validateModel = myModel.clone();
    applyTo(validateModel, findAll);

    ValidationInfo validationInfo = getValidationInfo(validateModel);

    if (validationInfo == null) {
      myModel.copyFrom(validateModel);
      updateFindSettings();

      super.doOKAction();
      myOkHandler.consume(myModel);
    }
    else {
      String message = validationInfo.message;
      Messages.showMessageDialog(
        myProject,
        message,
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
  }

  private void updateFindSettings() {
    FindSettings findSettings = FindSettings.getInstance();
    findSettings.setCaseSensitive(myModel.isCaseSensitive());
    if (myModel.isReplaceState()) {
      findSettings.setPreserveCase(myModel.isPreserveCase());
    }

    findSettings.setWholeWordsOnly(myModel.isWholeWordsOnly());
    boolean saveContextBetweenRestarts = false;
    findSettings.setInStringLiteralsOnly(saveContextBetweenRestarts && myModel.isInStringLiteralsOnly());
    findSettings.setInCommentsOnly(saveContextBetweenRestarts && myModel.isInCommentsOnly());
    findSettings.setExceptComments(saveContextBetweenRestarts && myModel.isExceptComments());
    findSettings.setExceptStringLiterals(saveContextBetweenRestarts && myModel.isExceptStringLiterals());
    findSettings.setExceptCommentsAndLiterals(saveContextBetweenRestarts && myModel.isExceptCommentsAndStringLiterals());

    findSettings.setRegularExpressions(myModel.isRegularExpressions());
    if (!myModel.isMultipleFiles()){
      findSettings.setForward(myModel.isForward());
      findSettings.setFromCursor(myModel.isFromCursor());

      findSettings.setGlobal(myModel.isGlobal());
    } else{
      String directoryName = myModel.getDirectoryName();
      if (directoryName != null && !directoryName.isEmpty()) {
        findSettings.setWithSubdirectories(myModel.isWithSubdirectories());
      }
      else if (myRbModule.isSelected()) {
      }
      else if (myRbCustomScope.isSelected()) {
        SearchScope selectedScope = myScopeCombo.getSelectedScope();
        String customScopeName = selectedScope == null ? null : selectedScope.getDisplayName();
        findSettings.setCustomScope(customScopeName);
      }
    }

    if (myCbToSkipResultsWhenOneUsage != null){
      findSettings.setSkipResultsWithOneUsage(
        isSkipResultsWhenOneUsage()
      );
    }

    findSettings.setFileMask(myModel.getFileFilter());
  }

  @Override
  protected boolean postponeValidation() {
    return true;
  }

  @Nullable("null means OK")
  private ValidationInfo getValidationInfo(@NotNull FindModel model) {
    if (myRbDirectory != null && myRbDirectory.isEnabled() && myRbDirectory.isSelected()) {
      PsiDirectory directory = FindInProjectUtil.getPsiDirectory(model, myProject);
      if (directory == null) {
        return new ValidationInfo(FindBundle.message("find.directory.not.found.error", getDirectory()), myDirectoryComboBox);
      }
    }

    if (!canSearchThisString()) {
      return new ValidationInfo("String to find is empty", myInputComboBox);
    }

    if (myCbRegularExpressions != null && myCbRegularExpressions.isSelected() && myCbRegularExpressions.isEnabled()) {
      String toFind = getStringToFind();
      try {
        boolean isCaseSensitive = myCbCaseSensitive != null && myCbCaseSensitive.isSelected() && myCbCaseSensitive.isEnabled();
        Pattern pattern =
          Pattern.compile(toFind, isCaseSensitive ? Pattern.MULTILINE : Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        if (pattern.matcher("").matches() && !toFind.endsWith("$") && !toFind.startsWith("^")) {
          return new ValidationInfo(FindBundle.message("find.empty.match.regular.expression.error"), myInputComboBox);
        }
      }
      catch (PatternSyntaxException e) {
        return new ValidationInfo(FindBundle.message("find.invalid.regular.expression.error", toFind, e.getDescription()), myInputComboBox);
      }
    }

    final String mask = getFileTypeMask();

    if (mask != null) {
      if (mask.isEmpty()) {
        return new ValidationInfo(FindBundle.message("find.filter.empty.file.mask.error"), myFileFilter);
      }

      if (mask.contains(";")) {
        return new ValidationInfo("File masks should be comma-separated", myFileFilter);
      }

      else {
        try {
          FindInProjectUtil.createFileMaskRegExp(mask);   // verify that the regexp compiles
        }
        catch (PatternSyntaxException ex) {
          return new ValidationInfo(FindBundle.message("find.filter.invalid.file.mask.error", mask), myFileFilter);
        }
      }
    }
    return null;
  }

  @Override
  protected ValidationInfo doValidate() {
    FindModel validateModel = myModel.clone();
    applyTo(validateModel, false);

    ValidationInfo result = getValidationInfo(validateModel);

    setOKStatus(result == null);

    return result;
  }

  @Override
  public void doHelpAction() {
    String id = myModel.isReplaceState()
                ? myModel.isMultipleFiles() ? HelpID.REPLACE_IN_PATH : HelpID.REPLACE_OPTIONS
                : myModel.isMultipleFiles() ? HelpID.FIND_IN_PATH : HelpID.FIND_OPTIONS;
    HelpManager.getInstance().invokeHelp(id);
  }

  private boolean isSkipResultsWhenOneUsage() {
    return myCbToSkipResultsWhenOneUsage!=null && myCbToSkipResultsWhenOneUsage.isSelected();
  }

  @NotNull
  private JPanel createFindOptionsPanel() {
    JPanel findOptionsPanel = new JPanel();
    findOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.options.group"), true));
    findOptionsPanel.setLayout(new BoxLayout(findOptionsPanel, BoxLayout.Y_AXIS));

    myCbCaseSensitive = createCheckbox(FindBundle.message("find.options.case.sensitive"));
    findOptionsPanel.add(myCbCaseSensitive);
    ItemListener liveResultsPreviewUpdateListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        scheduleResultsUpdate();
      }
    };
    myCbCaseSensitive.addItemListener(liveResultsPreviewUpdateListener);

    myCbPreserveCase = createCheckbox(FindBundle.message("find.options.replace.preserve.case"));
    myCbPreserveCase.addItemListener(liveResultsPreviewUpdateListener);
    findOptionsPanel.add(myCbPreserveCase);
    myCbPreserveCase.setVisible(myModel.isReplaceState());
    myCbWholeWordsOnly = createCheckbox(FindBundle.message("find.options.whole.words.only"));
    myCbWholeWordsOnly.addItemListener(liveResultsPreviewUpdateListener);

    findOptionsPanel.add(myCbWholeWordsOnly);

    myCbRegularExpressions = createCheckbox(FindBundle.message("find.options.regular.expressions"));
    myCbRegularExpressions.addItemListener(liveResultsPreviewUpdateListener);

    final JPanel regExPanel = new JPanel();
    regExPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    regExPanel.setLayout(new BoxLayout(regExPanel, BoxLayout.X_AXIS));
    regExPanel.add(myCbRegularExpressions);

    regExPanel.add(RegExHelpPopup.createRegExLink("[Help]", regExPanel, LOG));

    findOptionsPanel.add(regExPanel);

    mySearchContext = new ComboBox(new Object[] { getPresentableName(FindModel.SearchContext.ANY),
      getPresentableName(FindModel.SearchContext.IN_COMMENTS),
      getPresentableName(FindModel.SearchContext.IN_STRING_LITERALS),
      getPresentableName(FindModel.SearchContext.EXCEPT_COMMENTS),
      getPresentableName(FindModel.SearchContext.EXCEPT_STRING_LITERALS),
      getPresentableName(FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS)});
    mySearchContext.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        scheduleResultsUpdate();
      }
    });
    final JPanel searchContextPanel = new JPanel(new BorderLayout());
    searchContextPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel searchContextLabel = new JLabel(FindBundle.message("find.context.combo.label"));
    searchContextLabel.setLabelFor(mySearchContext);
    JPanel panel = new JPanel();
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.add(searchContextLabel);
    searchContextPanel.add(panel, BorderLayout.WEST);

    panel = new JPanel(new BorderLayout());
    panel.add(mySearchContext, BorderLayout.NORTH);
    searchContextPanel.add(panel, BorderLayout.CENTER);

    findOptionsPanel.add(searchContextPanel);

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myCbRegularExpressions.addActionListener(actionListener);
    myCbRegularExpressions.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        setupRegExpSetting();
      }
    });

    myCbCaseSensitive.addActionListener(actionListener);
    myCbPreserveCase.addActionListener(actionListener);

    return findOptionsPanel;
  }

  public static String getPresentableName(@NotNull FindModel.SearchContext searchContext) {
    @PropertyKey(resourceBundle = "messages.FindBundle") String messageKey = null;
    if (searchContext == FindModel.SearchContext.ANY) {
      messageKey = "find.context.anywhere.scope.label";
    } else if (searchContext == FindModel.SearchContext.EXCEPT_COMMENTS) {
      messageKey = "find.context.except.comments.scope.label";
    } else if (searchContext == FindModel.SearchContext.EXCEPT_STRING_LITERALS) {
      messageKey = "find.context.except.literals.scope.label";
    } else if (searchContext == FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS) {
      messageKey = "find.context.except.comments.and.literals.scope.label";
    } else if (searchContext == FindModel.SearchContext.IN_COMMENTS) {
      messageKey = "find.context.in.comments.scope.label";
    } else if (searchContext == FindModel.SearchContext.IN_STRING_LITERALS) {
      messageKey = "find.context.in.literals.scope.label";
    }
    return messageKey != null ? FindBundle.message(messageKey) : searchContext.toString();
  }

  private void setupRegExpSetting() {
    updateFileTypeForEditorComponent(myInputComboBox);
    if (myReplaceComboBox != null) updateFileTypeForEditorComponent(myReplaceComboBox);
  }

  private void updateFileTypeForEditorComponent(@NotNull ComboBox inputComboBox) {
    final Component editorComponent = inputComboBox.getEditor().getEditorComponent();

    if (editorComponent instanceof EditorTextField) {
      boolean isRegexp = myCbRegularExpressions.isSelectedWhenSelectable();
      FileType fileType = PlainTextFileType.INSTANCE;
      if (isRegexp) {
        Language regexpLanguage = Language.findLanguageByID("RegExp");
        if (regexpLanguage != null) {
          LanguageFileType regexpFileType = regexpLanguage.getAssociatedFileType();
          if (regexpFileType != null) {
            fileType = regexpFileType;
          }
        }
      }
      String fileName = isRegexp ? "a.regexp" : "a.txt";
      final PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(fileName, fileType, ((EditorTextField)editorComponent).getText(), -1, true);

      ((EditorTextField)editorComponent).setNewDocumentAndFileType(fileType, PsiDocumentManager.getInstance(myProject).getDocument(file));
    }
  }

  private void updateControls() {
    if (myCbRegularExpressions.isSelected()) {
      myCbWholeWordsOnly.makeUnselectable(false);
    }
    else {
      myCbWholeWordsOnly.makeSelectable();
    }
    if (myModel.isReplaceState()) {
      if (myCbRegularExpressions.isSelected() || myCbCaseSensitive.isSelected()) {
        myCbPreserveCase.makeUnselectable(false);
      }
      else {
        myCbPreserveCase.makeSelectable();
      }

      if (myCbPreserveCase.isSelected()) {
        myCbRegularExpressions.makeUnselectable(false);
        myCbCaseSensitive.makeUnselectable(false);
      }
      else {
        myCbRegularExpressions.makeSelectable();
        myCbCaseSensitive.makeSelectable();
      }
    }

    if (!myModel.isMultipleFiles()) {
      myRbFromCursor.setEnabled(myRbGlobal.isSelected());
      myRbEntireScope.setEnabled(myRbGlobal.isSelected());
    }
  }

  @NotNull
  private JPanel createDirectionPanel() {
    JPanel directionPanel = new JPanel();
    directionPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.direction.group"), true));
    directionPanel.setLayout(new BoxLayout(directionPanel, BoxLayout.Y_AXIS));

    myRbForward = new JRadioButton(FindBundle.message("find.direction.forward.radio"), true);
    directionPanel.add(myRbForward);
    myRbBackward = new JRadioButton(FindBundle.message("find.direction.backward.radio"));
    directionPanel.add(myRbBackward);
    ButtonGroup bgDirection = new ButtonGroup();
    bgDirection.add(myRbForward);
    bgDirection.add(myRbBackward);

    return directionPanel;
  }

  @NotNull
  private JComponent createGlobalScopePanel() {
    JPanel scopePanel = new JPanel();
    scopePanel.setLayout(new GridBagLayout());
    scopePanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.scope.group"), true));
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 3;
    gbConstraints.weightx = 1;
    final boolean canAttach = ProjectAttachProcessor.canAttachToProject();
    myRbProject = new JRadioButton(canAttach
                                   ? FindBundle.message("find.scope.all.projects.radio")
                                   : FindBundle.message("find.scope.whole.project.radio"), true);
    scopePanel.add(myRbProject, gbConstraints);
    ItemListener resultsPreviewUpdateListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        scheduleResultsUpdate();
      }
    };
    myRbProject.addItemListener(resultsPreviewUpdateListener);

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    myRbModule = new JRadioButton(canAttach
                                  ? FindBundle.message("find.scope.project.radio")
                                  : FindBundle.message("find.scope.module.radio"), false);
    scopePanel.add(myRbModule, gbConstraints);
    myRbModule.addItemListener(resultsPreviewUpdateListener);

    gbConstraints.gridx = 1;
    gbConstraints.gridwidth = 2;
    gbConstraints.weightx = 1;
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    String[] names = new String[modules.length];
    for (int i = 0; i < modules.length; i++) {
      names[i] = modules[i].getName();
    }

    Arrays.sort(names,String.CASE_INSENSITIVE_ORDER);
    myModuleComboBox = new ComboBox(names);
    myModuleComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        scheduleResultsUpdate();
      }
    });
    scopePanel.add(myModuleComboBox, gbConstraints);

    if (modules.length == 1) {
      myModuleComboBox.setVisible(false);
      myRbModule.setVisible(false);
    }

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    myRbDirectory = new JRadioButton(FindBundle.message("find.scope.directory.radio"), false);
    scopePanel.add(myRbDirectory, gbConstraints);
    myRbDirectory.addItemListener(resultsPreviewUpdateListener);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    myDirectoryComboBox = new ComboBox(200);
    Component editorComponent = myDirectoryComboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField) {
      JTextField field = (JTextField)editorComponent;
      field.setColumns(40);
    }
    initCombobox(myDirectoryComboBox);
    myDirectoryComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        scheduleResultsUpdate();
      }
    });
    scopePanel.add(myDirectoryComboBox, gbConstraints);

    gbConstraints.weightx = 0;
    gbConstraints.gridx = 2;
    mySelectDirectoryButton = new FixedSizeButton(myDirectoryComboBox);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(mySelectDirectoryButton, myDirectoryComboBox);
    mySelectDirectoryButton.setMargin(new Insets(0, 0, 0, 0));
    scopePanel.add(mySelectDirectoryButton, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 3;
    gbConstraints.insets = new Insets(0, 16, 0, 0);
    myCbWithSubdirectories = createCheckbox(true, FindBundle.message("find.scope.directory.recursive.checkbox"));
    myCbWithSubdirectories.setSelected(true);
    myCbWithSubdirectories.addItemListener(resultsPreviewUpdateListener);
    scopePanel.add(myCbWithSubdirectories, gbConstraints);


    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.insets = new Insets(0, 0, 0, 0);
    myRbCustomScope = new JRadioButton(FindBundle.message("find.scope.custom.radio"), false);
    scopePanel.add(myRbCustomScope, gbConstraints);

    gbConstraints.gridx++;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 2;
    myScopeCombo = new ScopeChooserCombo(myProject, true, true, FindSettings.getInstance().getDefaultScopeName());
    myScopeCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        scheduleResultsUpdate();
      }
    });
    myRbCustomScope.addItemListener(resultsPreviewUpdateListener);

    Disposer.register(myDisposable, myScopeCombo);
    scopePanel.add(myScopeCombo, gbConstraints);


    ButtonGroup bgScope = new ButtonGroup();
    bgScope.add(myRbDirectory);
    bgScope.add(myRbProject);
    bgScope.add(myRbModule);
    bgScope.add(myRbCustomScope);

    ActionListener validateAll = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
      }
    };
    myRbProject.addActionListener(validateAll);
    myRbCustomScope.addActionListener(validateAll);

    myRbDirectory.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
        myDirectoryComboBox.getEditor().getEditorComponent().requestFocusInWindow();
      }
    });

    myRbModule.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
        myModuleComboBox.requestFocusInWindow();
      }
    });

    mySelectDirectoryButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        FileChooser.chooseFiles(descriptor, myProject, null, new Consumer<List<VirtualFile>>() {
          @Override
          public void consume(final List<VirtualFile> files) {
            myDirectoryComboBox.setSelectedItem(files.get(0).getPresentableUrl());
          }
        });
      }
    });

    return scopePanel;
  }

  @NotNull
  private static StateRestoringCheckBox createCheckbox(@NotNull String message) {
    final StateRestoringCheckBox cb = new StateRestoringCheckBox(message);
    cb.setFocusable(false);
    return cb;
  }

  @NotNull
  private static StateRestoringCheckBox createCheckbox(boolean selected, @NotNull String message) {
    final StateRestoringCheckBox cb = new StateRestoringCheckBox(message, selected);
    cb.setFocusable(false);
    return cb;
  }

  private void validateScopeControls() {
    if (myRbDirectory.isSelected()) {
      myCbWithSubdirectories.makeSelectable();
    }
    else {
      myCbWithSubdirectories.makeUnselectable(myCbWithSubdirectories.isSelected());
    }
    myDirectoryComboBox.setEnabled(myRbDirectory.isSelected());
    mySelectDirectoryButton.setEnabled(myRbDirectory.isSelected());

    myModuleComboBox.setEnabled(myRbModule.isSelected());
    myScopeCombo.setEnabled(myRbCustomScope.isSelected());
  }

  @NotNull
  private JPanel createScopePanel() {
    JPanel scopePanel = new JPanel();
    scopePanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.scope.group"), true));
    scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.Y_AXIS));

    myRbGlobal = new JRadioButton(FindBundle.message("find.scope.global.radio"), true);
    scopePanel.add(myRbGlobal);
    myRbSelectedText = new JRadioButton(FindBundle.message("find.scope.selected.text.radio"));
    scopePanel.add(myRbSelectedText);
    ButtonGroup bgScope = new ButtonGroup();
    bgScope.add(myRbGlobal);
    bgScope.add(myRbSelectedText);

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myRbGlobal.addActionListener(actionListener);
    myRbSelectedText.addActionListener(actionListener);

    return scopePanel;
  }

  @NotNull
  private JPanel createOriginPanel() {
    JPanel originPanel = new JPanel();
    originPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.origin.group"), true));
    originPanel.setLayout(new BoxLayout(originPanel, BoxLayout.Y_AXIS));

    myRbFromCursor = new JRadioButton(FindBundle.message("find.origin.from.cursor.radio"), true);
    originPanel.add(myRbFromCursor);
    myRbEntireScope = new JRadioButton(FindBundle.message("find.origin.entire.scope.radio"));
    originPanel.add(myRbEntireScope);
    ButtonGroup bgOrigin = new ButtonGroup();
    bgOrigin.add(myRbFromCursor);
    bgOrigin.add(myRbEntireScope);

    return originPanel;
  }

  @NotNull
  private String getStringToFind() {
    String string = (String)myInputComboBox.getEditor().getItem();
    return string == null ? "" : string;
  }

  @NotNull
  private String getStringToReplace() {
    String item = (String)myReplaceComboBox.getEditor().getItem();
    return item == null ? "" : item;
  }

  private String getDirectory() {
    return (String)myDirectoryComboBox.getSelectedItem();
  }

  private static void setStringsToComboBox(@NotNull String[] strings, @NotNull ComboBox combo, String selected) {
    if (combo.getItemCount() > 0){
      combo.removeAllItems();
    }
    if (selected != null && selected.indexOf('\n') < 0) {
      strings = ArrayUtil.remove(strings, selected);
      // this ensures that last searched string will be selected if selected == ""
      if (!selected.isEmpty()) strings = ArrayUtil.append(strings, selected);
    }
    for(int i = strings.length - 1; i >= 0; i--){
      combo.addItem(strings[i]);
    }
  }

  private void setDirectories(@NotNull List<String> strings, String directoryName) {
    if (myDirectoryComboBox.getItemCount() > 0){
      myReplaceComboBox.removeAllItems();
    }
    if (directoryName != null && !directoryName.isEmpty()){
      if (strings.contains(directoryName)){
        strings.remove(directoryName);
      }
      myDirectoryComboBox.addItem(directoryName);
    }
    for(int i = strings.size() - 1; i >= 0; i--){
      myDirectoryComboBox.addItem(strings.get(i));
    }
    if (myDirectoryComboBox.getItemCount() == 0){
      myDirectoryComboBox.addItem("");
    }
  }

  private void applyTo(@NotNull FindModel model, boolean findAll) {
    model.setCaseSensitive(myCbCaseSensitive.isSelected());

    if (model.isReplaceState()) {
      model.setPreserveCase(myCbPreserveCase.isSelected());
    }

    model.setWholeWordsOnly(myCbWholeWordsOnly.isSelected());

    String selectedSearchContextInUi = (String)mySearchContext.getSelectedItem();
    FindModel.SearchContext searchContext = FindModel.SearchContext.ANY;
    if (FindBundle.message("find.context.in.literals.scope.label").equals(selectedSearchContextInUi)) {
      searchContext = FindModel.SearchContext.IN_STRING_LITERALS;
    }
    else if (FindBundle.message("find.context.in.comments.scope.label").equals(selectedSearchContextInUi)) {
      searchContext = FindModel.SearchContext.IN_COMMENTS;
    }
    else if (FindBundle.message("find.context.except.comments.scope.label").equals(selectedSearchContextInUi)) {
      searchContext = FindModel.SearchContext.EXCEPT_COMMENTS;
    }
    else if (FindBundle.message("find.context.except.literals.scope.label").equals(selectedSearchContextInUi)) {
      searchContext = FindModel.SearchContext.EXCEPT_STRING_LITERALS;
    } else if (FindBundle.message("find.context.except.comments.and.literals.scope.label").equals(selectedSearchContextInUi)) {
      searchContext = FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS;
    }

    model.setSearchContext(searchContext);

    model.setRegularExpressions(myCbRegularExpressions.isSelected());
    String stringToFind = getStringToFind();
    model.setStringToFind(stringToFind);

    if (model.isReplaceState()){
      model.setPromptOnReplace(true);
      model.setReplaceAll(false);
      String stringToReplace = getStringToReplace();
      model.setStringToReplace(StringUtil.convertLineSeparators(stringToReplace));
    }

    if (!model.isMultipleFiles()){
      model.setForward(myRbForward.isSelected());
      model.setFromCursor(myRbFromCursor.isSelected());
      model.setGlobal(myRbGlobal.isSelected());
    }
    else{
      if (myCbToOpenInNewTab != null){
        model.setOpenInNewTab(myCbToOpenInNewTab.isSelected());
      }

      model.setProjectScope(myRbProject.isSelected());
      model.setDirectoryName(null);
      model.setModuleName(null);
      model.setCustomScopeName(null);
      model.setCustomScope(null);
      model.setCustomScope(false);

      if (myRbDirectory.isSelected()) {
        String directory = getDirectory();
        model.setDirectoryName(directory == null ? "" : directory);
        model.setWithSubdirectories(myCbWithSubdirectories.isSelected());
      }
      else if (myRbModule.isSelected()) {
        model.setModuleName((String)myModuleComboBox.getSelectedItem());
      }
      else if (myRbCustomScope.isSelected()) {
        SearchScope selectedScope = myScopeCombo.getSelectedScope();
        String customScopeName = selectedScope == null ? null : selectedScope.getDisplayName();
        model.setCustomScopeName(customScopeName);
        model.setCustomScope(selectedScope == null ? null : selectedScope);
        model.setCustomScope(true);
      }
    }

    model.setFindAll(findAll);

    String mask = getFileTypeMask();
    model.setFileFilter(mask);
  }

  @Nullable
  private String getFileTypeMask() {
    String mask = null;
    if (myUseFileFilter !=null && myUseFileFilter.isSelected()) {
      mask = (String)myFileFilter.getSelectedItem();
    }
    return mask;
  }


  private void initByModel() {
    myCbCaseSensitive.setSelected(myModel.isCaseSensitive());
    myCbWholeWordsOnly.setSelected(myModel.isWholeWordsOnly());
    String searchContext = FindBundle.message("find.context.anywhere.scope.label");
    if (myModel.isInCommentsOnly()) searchContext = FindBundle.message("find.context.in.comments.scope.label");
    else if (myModel.isInStringLiteralsOnly()) searchContext = FindBundle.message("find.context.in.literals.scope.label");
    else if (myModel.isExceptStringLiterals()) searchContext = FindBundle.message("find.context.except.literals.scope.label");
    else if (myModel.isExceptComments()) searchContext = FindBundle.message("find.context.except.comments.scope.label");
    else if (myModel.isExceptCommentsAndStringLiterals()) searchContext = FindBundle.message("find.context.except.comments.and.literals.scope.label");
    mySearchContext.setSelectedItem(searchContext);

    myCbRegularExpressions.setSelected(myModel.isRegularExpressions());

    if (myModel.isMultipleFiles()) {
      final String dirName = myModel.getDirectoryName();
      setDirectories(FindSettings.getInstance().getRecentDirectories(), dirName);

      if (!StringUtil.isEmptyOrSpaces(dirName)) {
        VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(dirName);
        if (dir != null) {
          Module module = ModuleUtilCore.findModuleForFile(dir, myProject);
          if (module != null) {
            myModuleComboBox.setSelectedItem(module.getName());
          }
        }
      }
      if (myModel.isCustomScope()) {
        myRbCustomScope.setSelected(true);

        myScopeCombo.setEnabled(true);

        myCbWithSubdirectories.setEnabled(false);
        myDirectoryComboBox.setEnabled(false);
        mySelectDirectoryButton.setEnabled(false);
        myModuleComboBox.setEnabled(false);
      }
      else if (myModel.isProjectScope()) {
        myRbProject.setSelected(true);

        myCbWithSubdirectories.setEnabled(false);
        myDirectoryComboBox.setEnabled(false);
        mySelectDirectoryButton.setEnabled(false);
        myModuleComboBox.setEnabled(false);
        myScopeCombo.setEnabled(false);
      }
      else if (dirName != null) {
        myRbDirectory.setSelected(true);
        myCbWithSubdirectories.setEnabled(true);
        myDirectoryComboBox.setEnabled(true);
        mySelectDirectoryButton.setEnabled(true);
        myModuleComboBox.setEnabled(false);
        myScopeCombo.setEnabled(false);
      }
      else if (myModel.getModuleName() != null) {
        myRbModule.setSelected(true);

        myCbWithSubdirectories.setEnabled(false);
        myDirectoryComboBox.setEnabled(false);
        mySelectDirectoryButton.setEnabled(false);
        myModuleComboBox.setEnabled(true);
        myModuleComboBox.setSelectedItem(myModel.getModuleName());
        myScopeCombo.setEnabled(false);

        // force showing even if we have only one module
        myRbModule.setVisible(true);
        myModuleComboBox.setVisible(true);
      }
      else {
        assert false : myModel;
      }

      myCbWithSubdirectories.setSelected(myModel.isWithSubdirectories());

      if (myModel.getFileFilter()!=null && !myModel.getFileFilter().isEmpty()) {
        myFileFilter.setSelectedItem(myModel.getFileFilter());
        myFileFilter.setEnabled(true);
        myUseFileFilter.setSelected(true);
      }
    }
    else {
      if (myModel.isForward()){
        myRbForward.setSelected(true);
      }
      else{
        myRbBackward.setSelected(true);
      }

      if (myModel.isFromCursor()){
        myRbFromCursor.setSelected(true);
      }
      else{
        myRbEntireScope.setSelected(true);
      }

      if (myModel.isGlobal()){
        myRbGlobal.setSelected(true);
      }
      else{
        myRbSelectedText.setSelected(true);
      }
    }

    setStringsToComboBox(FindSettings.getInstance().getRecentFindStrings(), myInputComboBox, myModel.getStringToFind());
    if (myModel.isReplaceState()){
      myCbPreserveCase.setSelected(myModel.isPreserveCase());
      setStringsToComboBox(FindSettings.getInstance().getRecentReplaceStrings(), myReplaceComboBox, myModel.getStringToReplace());
    }
    updateControls();
  }

  private void navigateToSelectedUsage(JBTable source) {
    int[] rows = source.getSelectedRows();
    List<Usage> navigations = null;
    for(int row:rows) {
      Object valueAt = source.getModel().getValueAt(row, 0);
      if (valueAt instanceof Usage) {
        if (navigations == null) navigations = new SmartList<Usage>();
        Usage at = (Usage)valueAt;
        navigations.add(at);
      }
    }

    if (navigations != null) {
      applyTo(FindManager.getInstance(myProject).getFindInProjectModel(), false);
      doCancelAction();
      navigations.get(0).navigate(true);
      for(int i = 1; i < navigations.size(); ++i) navigations.get(i).highlightInEditor();
    }
  }

  private static class UsageTableCellRenderer extends JPanel implements TableCellRenderer {
    private ColoredTableCellRenderer myUsageRenderer = new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (value instanceof UsageInfo2UsageAdapter) {
          TextChunk[] text = ((UsageInfo2UsageAdapter)value).getPresentation().getText();

          // skip line number / file info
          for (int i = 1; i < text.length; ++i) {
            TextChunk textChunk = text[i];
            myUsageRenderer.append(textChunk.getText(), textChunk.getSimpleAttributesIgnoreBackground());
          }
        }
        setBorder(null);
      }
    };
    private ColoredTableCellRenderer myFileAndLineNumber = new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (value instanceof UsageInfo2UsageAdapter) {
          TextChunk[] text = ((UsageInfo2UsageAdapter)value).getPresentation().getText();
          // line number / file info
          append(((UsageInfo2UsageAdapter)value).getFile().getName() + " " + text[0].getText(), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
        }
        setBorder(null);
      }
    };

    UsageTableCellRenderer() {
      setLayout(new BorderLayout());

      add(myUsageRenderer, BorderLayout.WEST);
      add(myFileAndLineNumber, BorderLayout.EAST);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setBackground(UIUtil.getTableBackground(isSelected));
      myUsageRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      myFileAndLineNumber.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      return this;
    }
  }

  private class NavigateToSourceListener extends DoubleClickListener {

    @Override
    protected boolean onDoubleClick(MouseEvent event) {
      Object source = event.getSource();
      if (!(source instanceof JBTable)) return false;
      navigateToSelectedUsage((JBTable)source);
      return true;
    }

    @Override
    public void installOn(@NotNull final Component c) {
      super.installOn(c);

      if (c instanceof JBTable) {
        AnAction anAction = new AnAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            navigateToSelectedUsage((JBTable)c);
          }
        };

        String key = "navigate.to.usage";
        JComponent component = (JComponent)c;
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                                                                       key);
        component.getActionMap().put(key, new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            navigateToSelectedUsage((JBTable)c);
          }
        });
        //anAction.registerCustomShortcutSet(CommonShortcuts.ENTER, component);
        anAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), component);
      }
    }
  }
}

