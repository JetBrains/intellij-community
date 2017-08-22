/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.find.*;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FindDialog extends DialogWrapper implements FindUI {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.FindDialog");
  private final FindUIHelper myHelper;

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
  private FixedSizeButton mySelectDirectoryButton;
  private StateRestoringCheckBox myUseFileFilter;
  private ComboBox myFileFilter;
  private JCheckBox myCbToSkipResultsWhenOneUsage;
  private final Project myProject;
  private final Map<EditorTextField, DocumentListener> myComboBoxListeners = new HashMap<>();

  private Action myFindAllAction;
  private JRadioButton myRbCustomScope;
  private ScopeChooserCombo myScopeCombo;
  protected JLabel myReplacePrompt;
  private HideableTitledPanel myScopePanel;
  private static boolean myPreviousResultsExpandedState = true;
  private static boolean myPreviewResultsTabWasSelected;
  private static final int RESULTS_PREVIEW_TAB_INDEX = 1;

  private Splitter myPreviewSplitter;
  private JBTable myResultsPreviewTable;
  private UsagePreviewPanel myUsagePreviewPanel;
  private TabbedPane myContent;
  private Alarm mySearchRescheduleOnCancellationsAlarm;
  private static final String PREVIEW_TITLE = UIBundle.message("tool.window.name.preview");
  private volatile ProgressIndicatorBase myResultsPreviewSearchProgress;

  public FindDialog(FindUIHelper helper){
    super(helper.getProject(), true);
    myHelper = helper;
    myProject = myHelper.getProject();

    setTitle(myHelper.getTitle());
    setOKButtonText(FindBundle.message("find.button"));
    init();
  }

  @Override
  public void showUI() {
    if (haveResultsPreview()) {
      ApplicationManager.getApplication().invokeLater(this::scheduleResultsUpdate, ModalityState.any());
    }
    show();
  }

  @Override
  public void doCancelAction() { // doCancel disposes fields and then calls dispose
    applyTo(FindManager.getInstance(myProject).getFindInProjectModel(), false);
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
    for(Map.Entry<EditorTextField, DocumentListener> e: myComboBoxListeners.entrySet()) {
      e.getKey().removeDocumentListener(e.getValue());
    }
    myComboBoxListeners.clear();
    if (myScopePanel != null) myPreviousResultsExpandedState = myScopePanel.isExpanded();
    super.dispose();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myInputComboBox;
  }

  @Override
  protected String getDimensionServiceKey() {
    return myHelper.getModel().isReplaceState() ? "replaceTextDialog" : "findTextDialog";
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    FindModel myModel = myHelper.getModel();
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

  private void revealWhitespaces(@NotNull ComboBox comboBox) {
    ComboBoxEditor comboBoxEditor = new RevealingSpaceComboboxEditor(myProject, comboBox);
    comboBox.setEditor(comboBoxEditor);
    comboBox.setRenderer(new EditorComboBoxRenderer(comboBoxEditor));
  }

  private void initCombobox(@NotNull final ComboBox comboBox) {
    comboBox.setEditable(true);
    comboBox.setMaximumRowCount(8);

    comboBox.addActionListener(__ -> validateFindButton());

    final Component editorComponent = comboBox.getEditor().getEditorComponent();

    if (editorComponent instanceof EditorTextField) {
      final EditorTextField etf = (EditorTextField) editorComponent;

      DocumentListener listener = new DocumentListener() {
        @Override
        public void documentChanged(final DocumentEvent e) {
          handleComboBoxValueChanged(comboBox);
        }
      };
      etf.addDocumentListener(listener);
      myComboBoxListeners.put(etf, listener);
    }
    else {
      if (editorComponent instanceof JTextComponent) {
        final javax.swing.text.Document document = ((JTextComponent)editorComponent).getDocument();
        final DocumentAdapter documentAdapter = new DocumentAdapter() {
          @Override
          protected void textChanged(javax.swing.event.DocumentEvent e) {
            handleAnyComboBoxValueChanged(comboBox);
          }
        };
        document.addDocumentListener(documentAdapter);
        Disposer.register(myDisposable, () -> document.removeDocumentListener(documentAdapter));
      } else {
        assert false;
      }
    }

    if (!myHelper.getModel().isReplaceState()) {
      makeResultsPreviewActionOverride(
        comboBox,
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
        "choosePrevious",
        () -> {
          int row = myResultsPreviewTable.getSelectedRow();
          if (row > 0) myResultsPreviewTable.setRowSelectionInterval(row - 1, row - 1);
          TableUtil.scrollSelectionToVisible(myResultsPreviewTable);
        }
      );

      makeResultsPreviewActionOverride(
        comboBox,
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
        "chooseNext",
        () -> {
          int row = myResultsPreviewTable.getSelectedRow();
          if (row >= -1 && row + 1 < myResultsPreviewTable.getRowCount()) {
            myResultsPreviewTable.setRowSelectionInterval(row + 1, row + 1);
            TableUtil.scrollSelectionToVisible(myResultsPreviewTable);
          }
        }
      );

      makeResultsPreviewActionOverride(
        comboBox,
        KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
        "scrollUp",
        () -> ScrollingUtil.movePageUp(myResultsPreviewTable)
      );

      makeResultsPreviewActionOverride(
        comboBox,
        KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
        "scrollDown",
        () -> ScrollingUtil.movePageDown(myResultsPreviewTable)
      );

      AnAction action = new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (isResultsPreviewTabActive()) {
            navigateToSelectedUsage(myResultsPreviewTable);
          }
        }
      };
      action.registerCustomShortcutSet(CommonShortcuts.getEditSource(), comboBox, myDisposable);
      new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (!isResultsPreviewTabActive() || myResultsPreviewTable.getSelectedRowCount() == 0) doOKAction();
          else action.actionPerformed(e);
        }
      }.registerCustomShortcutSet(CommonShortcuts.ENTER, comboBox, myDisposable);
    }
  }

  private boolean isResultsPreviewTabActive() {
    return myResultsPreviewTable != null &&
        myContent.getSelectedIndex() == RESULTS_PREVIEW_TAB_INDEX;
  }

  private void makeResultsPreviewActionOverride(final JComboBox component, KeyStroke keyStroke, String newActionKey, final Runnable newAction) {
    InputMap inputMap = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    Object action = inputMap.get(keyStroke);
    inputMap.put(keyStroke, newActionKey);
    final Action previousAction = action != null ? component.getActionMap().get(action) : null;
    component.getActionMap().put(newActionKey, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if(isResultsPreviewTabActive() && !component.isPopupVisible()) {
          newAction.run();
          return;
        }

        if (previousAction != null) {
          previousAction.actionPerformed(e);
        }
      }
    });
  }

  private void handleComboBoxValueChanged(@NotNull ComboBox comboBox) {
    Object item = comboBox.getEditor().getItem();
    if (item != null && !item.equals(comboBox.getSelectedItem())){
      comboBox.setSelectedItem(item);
    }

    handleAnyComboBoxValueChanged(comboBox);
  }

  private void handleAnyComboBoxValueChanged(@NotNull ComboBox comboBox) {
    if (comboBox != myReplaceComboBox) scheduleResultsUpdate();
    validateFindButton();
  }

  private void findSettingsChanged() {
    if (haveResultsPreview()) {
      final ModalityState state = ModalityState.current();
      if (state == ModalityState.NON_MODAL) return; // skip initial changes

      finishPreviousPreviewSearch();
      mySearchRescheduleOnCancellationsAlarm.cancelAllRequests();
      final FindModel findModel = myHelper.getModel().clone();
      applyTo(findModel, false);

      ValidationInfo result = getValidationInfo(findModel);

      final ProgressIndicatorBase progressIndicatorWhenSearchStarted = new ProgressIndicatorBase();
      myResultsPreviewSearchProgress = progressIndicatorWhenSearchStarted;

      final DefaultTableModel model = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };

      // Use previously shown usage files as hint for faster search and better usage preview performance if pattern length increased
      final LinkedHashSet<VirtualFile> filesToScanInitially = new LinkedHashSet<>();

      if (myHelper.myPreviousModel != null && myHelper.myPreviousModel.getStringToFind().length() < findModel.getStringToFind().length()) {
        final DefaultTableModel previousModel = (DefaultTableModel)myResultsPreviewTable.getModel();
        for (int i = 0, len = previousModel.getRowCount(); i < len; ++i) {
          final UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)previousModel.getValueAt(i, 0);
          final VirtualFile file = usage.getFile();
          if (file != null) filesToScanInitially.add(file);
        }
      }

      myHelper.myPreviousModel = findModel;

      model.addColumn("Usages");

      myResultsPreviewTable.setModel(model);

      if (result != null) {
        myResultsPreviewTable.getEmptyText().setText(UIBundle.message("message.nothingToShow"));
        myContent.setTitleAt(RESULTS_PREVIEW_TAB_INDEX, PREVIEW_TITLE);
        return;
      }

      GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(
        FindInProjectUtil.getScopeFromModel(myProject, findModel), myProject);
      myResultsPreviewTable.getColumnModel().getColumn(0).setCellRenderer(new UsageTableCellRenderer(false, true, scope));

      myResultsPreviewTable.getEmptyText().setText("Searching...");
      myContent.setTitleAt(RESULTS_PREVIEW_TAB_INDEX, PREVIEW_TITLE);

      final Component component = myInputComboBox.getEditor().getEditorComponent();

      // avoid commit of search text document upon encountering / highlighting of first usage that will restart the search
      // (UsagePreviewPanel.highlight)
      if (component instanceof EditorTextField) {
        final Document document = ((EditorTextField)component).getDocument();
        if (document != null) {
          PsiDocumentManager.getInstance(myProject).commitDocument(document);
        }
      }

      final AtomicInteger resultsCount = new AtomicInteger();
      final AtomicInteger resultsFilesCount = new AtomicInteger();

      ProgressIndicatorUtils.scheduleWithWriteActionPriority(myResultsPreviewSearchProgress, new ReadTask() {

        @Nullable
        @Override
        public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
          final UsageViewPresentation presentation =
            FindInProjectUtil.setupViewPresentation(FindSettings.getInstance().isShowResultsInSeparateView(), findModel);
          final boolean showPanelIfOnlyOneUsage = !FindSettings.getInstance().isSkipResultsWithOneUsage();

          final FindUsagesProcessPresentation processPresentation =
            FindInProjectUtil.setupProcessPresentation(myProject, showPanelIfOnlyOneUsage, presentation);
          ThreadLocal<VirtualFile> lastUsageFileRef = new ThreadLocal<>();

          FindInProjectUtil.findUsages(findModel, myProject, info -> {
            if(isCancelled()) {
              return false;
            }
            final Usage usage = UsageInfo2UsageAdapter.CONVERTER.fun(info);
            usage.getPresentation().getIcon(); // cache icon

            VirtualFile file = lastUsageFileRef.get();
            VirtualFile usageFile = info.getVirtualFile();
            if (file == null || !file.equals(usageFile)) {
              resultsFilesCount.incrementAndGet();
              lastUsageFileRef.set(usageFile);
            }

            ApplicationManager.getApplication().invokeLater(() -> {
              if (isCancelled()) return;
              model.addRow(new Object[]{usage});
            }, state);
            return resultsCount.incrementAndGet() < ShowUsagesAction.getUsagesPageSize();
          }, processPresentation, filesToScanInitially);

          boolean succeeded = !progressIndicatorWhenSearchStarted.isCanceled();
          if (succeeded) {
            return new Continuation(() -> {
              if (!isCancelled()) {
                int occurrences = resultsCount.get();
                int filesWithOccurrences = resultsFilesCount.get();
                if (occurrences == 0) myResultsPreviewTable.getEmptyText().setText(UIBundle.message("message.nothingToShow"));
                boolean foundAllUsages = occurrences < ShowUsagesAction.getUsagesPageSize();

                myContent.setTitleAt(RESULTS_PREVIEW_TAB_INDEX,
                                     PREVIEW_TITLE +
                                     " (" + (foundAllUsages ? Integer.valueOf(occurrences) : occurrences + "+") +
                                     UIBundle.message("message.matches", occurrences) +
                                     " in " + (foundAllUsages ? Integer.valueOf(filesWithOccurrences) : filesWithOccurrences + "+") +
                                     UIBundle.message("message.files", filesWithOccurrences) +
                                     ")");
              }
            }, state);
          }
          return null;
        }

        boolean isCancelled() {
          return progressIndicatorWhenSearchStarted != myResultsPreviewSearchProgress || myResultsPreviewSearchProgress.isCanceled();
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
    final Alarm alarm = mySearchRescheduleOnCancellationsAlarm;
    if (alarm == null || alarm.isDisposed()) return;
    alarm.cancelAllRequests();
    alarm.addRequest(() -> TransactionGuard.submitTransaction(myDisposable, this::findSettingsChanged), 100);
  }

  private void finishPreviousPreviewSearch() {
    if (myResultsPreviewSearchProgress != null && !myResultsPreviewSearchProgress.isCanceled()) {
      myResultsPreviewSearchProgress.cancel();
    }
  }

  private void validateFindButton() {
    boolean okStatus = myHelper.canSearchThisString() ||
                       myRbDirectory != null && myRbDirectory.isSelected() && StringUtil.isEmpty(getDirectory());
    setOKStatus(okStatus);
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
    
    if (myHelper.getModel().isMultipleFiles()) {
      optionsPanel.add(createGlobalScopePanel(), gbConstraints);
      gbConstraints.weightx = 1;
      gbConstraints.weighty = 1;
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;

      gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      optionsPanel.add(createFilterPanel(),gbConstraints);

      myCbToSkipResultsWhenOneUsage = createCheckbox(myHelper.isSkipResultsWithOneUsage(), FindBundle.message("find.options.skip.results.tab.with.one.occurrence.checkbox"));
      myCbToSkipResultsWhenOneUsage.addActionListener(e -> myHelper.setSkipResultsWithOneUsage(myCbToSkipResultsWhenOneUsage.isSelected()));
      resultsOptionPanel = createResultsOptionPanel(optionsPanel, gbConstraints);
      resultsOptionPanel.add(myCbToSkipResultsWhenOneUsage);

      myCbToSkipResultsWhenOneUsage.setVisible(!myHelper.isReplaceState());

      if (haveResultsPreview()) {
        final JBTable table = new JBTable() {
          @Override
          public Dimension getPreferredSize() {
            return new Dimension(myInputComboBox.getWidth(), super.getPreferredSize().height);
          }
        };
        table.setShowColumns(false);
        table.setShowGrid(false);
        table.setIntercellSpacing(JBUI.emptySize());
        new NavigateToSourceListener().installOn(table);

        Splitter previewSplitter = new Splitter(true, 0.5f, 0.1f, 0.9f);
        myUsagePreviewPanel = new UsagePreviewPanel(myProject, new UsageViewPresentation(), true);
        myUsagePreviewPanel.setBorder(IdeBorderFactory.createBorder());
        registerNavigateToSourceShortcutOnComponent(table, myUsagePreviewPanel);
        myResultsPreviewTable = table;
        new TableSpeedSearch(table, o -> ((UsageInfo2UsageAdapter)o).getFile().getName());
        myResultsPreviewTable.getSelectionModel().addListSelectionListener(e -> {
          if (e.getValueIsAdjusting()) return;
          int index = myResultsPreviewTable.getSelectionModel().getLeadSelectionIndex();
          if (index != -1) {
            UsageInfo usageInfo = ((UsageInfo2UsageAdapter)myResultsPreviewTable.getModel().getValueAt(index, 0)).getUsageInfo();
            myUsagePreviewPanel.updateLayout(usageInfo.isValid() ? Collections.singletonList(usageInfo) : null);
            VirtualFile file = usageInfo.getVirtualFile();
            myUsagePreviewPanel.setBorder(IdeBorderFactory.createTitledBorder(file != null ? file.getPath() : "", false));
          }
          else {
            myUsagePreviewPanel.updateLayout(null);
            myUsagePreviewPanel.setBorder(IdeBorderFactory.createBorder());
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

    if (myHelper.getModel().isOpenInNewTabVisible()){
      myCbToOpenInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
      myCbToOpenInNewTab.setFocusable(false);
      myCbToOpenInNewTab.setSelected(myHelper.isUseSeparateView());
      myCbToOpenInNewTab.setEnabled(myHelper.getModel().isOpenInNewTabEnabled());
      myCbToOpenInNewTab.addActionListener(e -> myHelper.setUseSeparateView(myCbToOpenInNewTab.isSelected()));

      if (resultsOptionPanel == null) resultsOptionPanel = createResultsOptionPanel(optionsPanel, gbConstraints);
      resultsOptionPanel.add(myCbToOpenInNewTab);
    }

    if (myPreviewSplitter != null) {
      TabbedPane pane = new JBTabsPaneImpl(myProject, SwingConstants.TOP, myDisposable);
      pane.insertTab("Options", null, optionsPanel, null, 0);
      pane.insertTab(PREVIEW_TITLE, null, myPreviewSplitter, null, RESULTS_PREVIEW_TAB_INDEX);
      myContent = pane;
      final AnAction anAction = new DumbAwareAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          int selectedIndex = myContent.getSelectedIndex();
          myContent.setSelectedIndex(1 - selectedIndex);
        }
      };

      final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_SWITCHER).getShortcutSet();

      anAction.registerCustomShortcutSet(shortcutSet, getRootPane(), myDisposable);

      if (myPreviewResultsTabWasSelected) myContent.setSelectedIndex(RESULTS_PREVIEW_TAB_INDEX);

      return pane.getComponent();
    }

    return optionsPanel;
  }

  private boolean haveResultsPreview() {
    return Registry.is("ide.find.show.preview") && myHelper.getModel().isMultipleFiles();
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
    myUseFileFilter.addActionListener(__ -> {
      scheduleResultsUpdate();
      validateFindButton();
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
      __ -> {
        if (useFileFilter.isSelected()) {
          fileFilter.setEnabled(true);
          fileFilter.getEditor().selectAll();
          fileFilter.getEditor().getEditorComponent().requestFocusInWindow();
        }
        else {
          fileFilter.setEnabled(false);
        }
      }
    );
  }

  @Override
  public void doOKAction() {
    doOKAction(false);
  }

  private void doOKAction(boolean findAll) {
    FindModel validateModel = myHelper.getModel().clone();
    applyTo(validateModel, findAll);

    ValidationInfo validationInfo = getValidationInfo(validateModel);

    if (validationInfo == null) {
      myHelper.getModel().copyFrom(validateModel);
      myHelper.updateFindSettings();

      rememberResultsPreviewWasOpen();
      super.doOKAction();
      myHelper.doOKAction();
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

  @Nullable("null means OK")
  private ValidationInfo getValidationInfo(@NotNull FindModel model) {
    if (myRbDirectory != null && myRbDirectory.isEnabled() && myRbDirectory.isSelected()) {
      VirtualFile directory = FindInProjectUtil.getDirectory(model);
      if (directory == null) {
        return new ValidationInfo(FindBundle.message("find.directory.not.found.error", getDirectory()), myDirectoryComboBox);
      }
    }

    if (!myHelper.canSearchThisString()) {
      return new ValidationInfo(FindBundle.message("find.empty.search.text.error"), myInputComboBox);
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
          FindInProjectUtil.createFileMaskCondition(mask);   // verify that the regexp compiles
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
    FindModel validateModel = myHelper.getModel().clone();
    applyTo(validateModel, false);

    ValidationInfo result = getValidationInfo(validateModel);

    setOKStatus(result == null);

    return result;
  }

  @Override
  public void doHelpAction() {
    FindModel myModel = myHelper.getModel();
    String id = myModel.isReplaceState()
                ? myModel.isMultipleFiles() ? HelpID.REPLACE_IN_PATH : HelpID.REPLACE_OPTIONS
                : myModel.isMultipleFiles() ? HelpID.FIND_IN_PATH : HelpID.FIND_OPTIONS;
    HelpManager.getInstance().invokeHelp(id);
  }

  @NotNull
  private JPanel createFindOptionsPanel() {
    JPanel findOptionsPanel = new JPanel();
    findOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.options.group"), true));
    findOptionsPanel.setLayout(new BoxLayout(findOptionsPanel, BoxLayout.Y_AXIS));

    myCbCaseSensitive = createCheckbox(FindBundle.message("find.options.case.sensitive"));
    findOptionsPanel.add(myCbCaseSensitive);
    ItemListener liveResultsPreviewUpdateListener = __ -> scheduleResultsUpdate();
    myCbCaseSensitive.addItemListener(liveResultsPreviewUpdateListener);

    myCbPreserveCase = createCheckbox(FindBundle.message("find.options.replace.preserve.case"));
    myCbPreserveCase.addItemListener(liveResultsPreviewUpdateListener);
    findOptionsPanel.add(myCbPreserveCase);
    myCbPreserveCase.setVisible(myHelper.isReplaceState());
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
    mySearchContext.addActionListener(__ -> scheduleResultsUpdate());
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

    ActionListener actionListener = __ -> updateControls();
    myCbRegularExpressions.addActionListener(actionListener);
    myCbRegularExpressions.addItemListener(__ -> setupRegExpSetting());

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

  @NotNull
  static FindModel.SearchContext parseSearchContext(String presentableName) {
    FindModel.SearchContext searchContext = FindModel.SearchContext.ANY;
    if (FindBundle.message("find.context.in.literals.scope.label").equals(presentableName)) {
      searchContext = FindModel.SearchContext.IN_STRING_LITERALS;
    }
    else if (FindBundle.message("find.context.in.comments.scope.label").equals(presentableName)) {
      searchContext = FindModel.SearchContext.IN_COMMENTS;
    }
    else if (FindBundle.message("find.context.except.comments.scope.label").equals(presentableName)) {
      searchContext = FindModel.SearchContext.EXCEPT_COMMENTS;
    }
    else if (FindBundle.message("find.context.except.literals.scope.label").equals(presentableName)) {
      searchContext = FindModel.SearchContext.EXCEPT_STRING_LITERALS;
    } else if (FindBundle.message("find.context.except.comments.and.literals.scope.label").equals(presentableName)) {
      searchContext = FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS;
    }
    return searchContext;
  }

  @NotNull
  static String getSearchContextName(FindModel model) {
    String searchContext = FindBundle.message("find.context.anywhere.scope.label");
    if (model.isInCommentsOnly()) searchContext = FindBundle.message("find.context.in.comments.scope.label");
    else if (model.isInStringLiteralsOnly()) searchContext = FindBundle.message("find.context.in.literals.scope.label");
    else if (model.isExceptStringLiterals()) searchContext = FindBundle.message("find.context.except.literals.scope.label");
    else if (model.isExceptComments()) searchContext = FindBundle.message("find.context.except.comments.scope.label");
    else if (model.isExceptCommentsAndStringLiterals()) searchContext = FindBundle.message("find.context.except.comments.and.literals.scope.label");
    return searchContext;
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
    if (myHelper.isReplaceState()) {
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

    if (!myHelper.getModel().isMultipleFiles()) {
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
    ItemListener resultsPreviewUpdateListener = __ -> scheduleResultsUpdate();
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
    myModuleComboBox.addActionListener(__ -> scheduleResultsUpdate());
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
    myDirectoryComboBox.setSwingPopup(false);
    myDirectoryComboBox.addActionListener(__ -> scheduleResultsUpdate());
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
    myScopeCombo = new ScopeChooserCombo();
    myScopeCombo.init(myProject, true, true, FindSettings.getInstance().getDefaultScopeName(), new Condition<ScopeDescriptor>() {
      //final String projectFilesScopeName = PsiBundle.message("psi.search.scope.project");
      private final String moduleFilesScopeName;
      {
        String moduleScopeName = PsiBundle.message("search.scope.module", "");
        final int ind = moduleScopeName.indexOf(' ');
        moduleFilesScopeName = moduleScopeName.substring(0, ind + 1);
      }
      @Override
      public boolean value(ScopeDescriptor descriptor) {
        final String display = descriptor.getDisplay();
        return /*!projectFilesScopeName.equals(display) &&*/ !display.startsWith(moduleFilesScopeName);
      }
    });
    myScopeCombo.getComboBox().addActionListener(__ -> scheduleResultsUpdate());
    myRbCustomScope.addItemListener(resultsPreviewUpdateListener);

    Disposer.register(myDisposable, myScopeCombo);
    scopePanel.add(myScopeCombo, gbConstraints);


    ButtonGroup bgScope = new ButtonGroup();
    bgScope.add(myRbProject);
    bgScope.add(myRbModule);
    bgScope.add(myRbDirectory);
    bgScope.add(myRbCustomScope);

    myRbProject.addActionListener(__ -> {
      validateScopeControls();
      validateFindButton();
    });
    myRbCustomScope.addActionListener(__ -> {
      validateScopeControls();
      validateFindButton();
      myScopeCombo.getComboBox().requestFocusInWindow();
    });

    myRbDirectory.addActionListener(__ -> {
      validateScopeControls();
      validateFindButton();
      myDirectoryComboBox.getEditor().getEditorComponent().requestFocusInWindow();
    });

    myRbModule.addActionListener(__ -> {
      validateScopeControls();
      validateFindButton();
      myModuleComboBox.requestFocusInWindow();
    });

    mySelectDirectoryButton.addActionListener(__ -> {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      FileChooser.chooseFiles(descriptor, myProject, null, files -> myDirectoryComboBox.setSelectedItem(files.get(0).getPresentableUrl()));
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

    ActionListener actionListener = __ -> updateControls();
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

  @Override
  @NotNull
  public String getStringToFind() {
    String string = (String)myInputComboBox.getEditor().getItem();
    return string == null ? "" : string;
  }

  @NotNull
  private String getStringToReplace() {
    String item = (String)myReplaceComboBox.getEditor().getItem();
    return item == null ? "" : item;
  }

  private String getDirectory() {
    return (String)myDirectoryComboBox.getEditor().getItem();
  }

  private static void setStringsToComboBox(@NotNull String[] strings, @NotNull ComboBox<String> combo, String selected) {
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
    int ignoredIdx = -1;
    if (directoryName != null && !directoryName.isEmpty()){
      ignoredIdx = strings.indexOf(directoryName);
      myDirectoryComboBox.addItem(directoryName);
    }
    for(int i = strings.size() - 1; i >= 0; i--){
      if (i == ignoredIdx) continue;
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
    FindModel.SearchContext searchContext = parseSearchContext(selectedSearchContextInUi);

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
        model.setCustomScope(selectedScope);
        model.setCustomScope(true);
      }
    }

    model.setFindAll(findAll);

    String mask = getFileTypeMask();
    model.setFileFilter(mask);
  }

  @Override
  @Nullable
  public String getFileTypeMask() {
    String mask = null;
    if (myUseFileFilter !=null && myUseFileFilter.isSelected()) {
      mask = (String)myFileFilter.getEditor().getItem();
    }
    return mask;
  }

  @Override
  public void initByModel() {
    FindModel myModel = myHelper.getModel();
    myCbCaseSensitive.setSelected(myModel.isCaseSensitive());
    myCbWholeWordsOnly.setSelected(myModel.isWholeWordsOnly());
    String searchContext = getSearchContextName(myModel);
    mySearchContext.setSelectedItem(searchContext);

    myCbRegularExpressions.setSelected(myModel.isRegularExpressions());

    if (myModel.isMultipleFiles()) {
      final String dirName = myModel.getDirectoryName();
      setDirectories(FindInProjectSettings.getInstance(myProject).getRecentDirectories(), dirName);

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

    FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(myProject);
    setStringsToComboBox(findInProjectSettings.getRecentFindStrings(), myInputComboBox, myModel.getStringToFind());
    if (myModel.isReplaceState()){
      myCbPreserveCase.setSelected(myModel.isPreserveCase());
      setStringsToComboBox(findInProjectSettings.getRecentReplaceStrings(), myReplaceComboBox, myModel.getStringToReplace());
    }
    updateControls();
    boolean isReplaceState = myModel.isReplaceState();
    myReplacePrompt.setVisible(isReplaceState);
    myReplaceComboBox.setVisible(isReplaceState);
    if (myCbToSkipResultsWhenOneUsage != null) {
      myCbToSkipResultsWhenOneUsage.setVisible(!isReplaceState);
    }
    myCbPreserveCase.setVisible(isReplaceState);
    setTitle(myHelper.getTitle());
    validateFindButton();
  }

  private void navigateToSelectedUsage(JBTable source) {
    int[] rows = source.getSelectedRows();
    List<Usage> navigations = null;
    for(int row:rows) {
      Object valueAt = source.getModel().getValueAt(row, 0);
      if (valueAt instanceof Usage) {
        if (navigations == null) navigations = new SmartList<>();
        Usage at = (Usage)valueAt;
        navigations.add(at);
      }
    }

    if (navigations != null) {
      doCancelAction();
      navigations.get(0).navigate(true);
      for(int i = 1; i < navigations.size(); ++i) navigations.get(i).highlightInEditor();
    }
  }

  static class UsageTableCellRenderer extends JPanel implements TableCellRenderer {
    private final ColoredTableCellRenderer myUsageRenderer = new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (value instanceof UsageInfo2UsageAdapter) {
          if (!((UsageInfo2UsageAdapter)value).isValid()) {
            myUsageRenderer.append(" "+UsageViewBundle.message("node.invalid") + " ", SimpleTextAttributes.ERROR_ATTRIBUTES);
          }
          TextChunk[] text = ((UsageInfo2UsageAdapter)value).getPresentation().getText();

          // skip line number / file info
          for (int i = 1; i < text.length; ++i) {
            TextChunk textChunk = text[i];
            SimpleTextAttributes attributes = getAttributes(textChunk);
            myUsageRenderer.append(textChunk.getText(), attributes);
          }
        }
        setBorder(null);
      }

      @NotNull
      private SimpleTextAttributes getAttributes(@NotNull TextChunk textChunk) {
        SimpleTextAttributes at = textChunk.getSimpleAttributesIgnoreBackground();
        if (myUseBold) return at;
        boolean highlighted = textChunk.getType() != null || at.getFontStyle() == Font.BOLD;
        return highlighted
               ? new SimpleTextAttributes(null, at.getFgColor(), at.getWaveColor(),
                                          (at.getStyle() & ~SimpleTextAttributes.STYLE_BOLD) |
                                          SimpleTextAttributes.STYLE_SEARCH_MATCH)
               : at;
      }
    };
    private final ColoredTableCellRenderer myFileAndLineNumber = new ColoredTableCellRenderer() {
      private final SimpleTextAttributes REPEATED_FILE_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xCCCCCC, 0x5E5E5E));
      private final SimpleTextAttributes ORDINAL_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, new JBColor(0x999999, 0x999999));
      
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (value instanceof UsageInfo2UsageAdapter) {
          UsageInfo2UsageAdapter usageAdapter = (UsageInfo2UsageAdapter)value;
          TextChunk[] text = usageAdapter.getPresentation().getText();
          // line number / file info
          VirtualFile file = usageAdapter.getFile();
          String uniqueVirtualFilePath = getFilePath(usageAdapter);
          VirtualFile prevFile = findPrevFile(table, row, column);
          SimpleTextAttributes attributes = Comparing.equal(file, prevFile) ? REPEATED_FILE_ATTRIBUTES : ORDINAL_ATTRIBUTES;
          append(uniqueVirtualFilePath, attributes);
          if (text.length > 0) append(" " + text[0].getText(), ORDINAL_ATTRIBUTES);
        }
        setBorder(null);
      }

      @NotNull
      private String getFilePath(@NotNull UsageInfo2UsageAdapter ua) {
        String uniquePath =
          UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(ua.getUsageInfo().getProject(), ua.getFile(), myScope);
        return myOmitFileExtension ? FileUtilRt.getNameWithoutExtension(uniquePath) : uniquePath;
      }

      @Nullable
      private VirtualFile findPrevFile(@NotNull JTable table, int row, int column) {
        if (row <= 0) return null;
        Object prev = table.getValueAt(row - 1, column);
        return prev instanceof UsageInfo2UsageAdapter ? ((UsageInfo2UsageAdapter)prev).getFile() : null;
      }
    };

    private static final int MARGIN = 2;
    private final boolean myOmitFileExtension;
    private final boolean myUseBold;
    private final GlobalSearchScope myScope;

    UsageTableCellRenderer(boolean omitFileExtension, boolean useBold, GlobalSearchScope scope) {
      myOmitFileExtension = omitFileExtension;
      myUseBold = useBold;
      myScope = scope;
      setLayout(new BorderLayout());
      add(myUsageRenderer, BorderLayout.CENTER);
      add(myFileAndLineNumber, BorderLayout.EAST);
      setBorder(JBUI.Borders.empty(MARGIN, MARGIN, MARGIN, 0));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myUsageRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      myFileAndLineNumber.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setBackground(myUsageRenderer.getBackground());
      if (!isSelected && value instanceof UsageInfo2UsageAdapter) {
        UsageInfo2UsageAdapter usageAdapter = (UsageInfo2UsageAdapter)value;
        Color color = FileColorManager.getInstance(usageAdapter.getUsageInfo().getProject()).getFileColor(usageAdapter.getFile());
        setBackground(color);
        myUsageRenderer.setBackground(color);
        myFileAndLineNumber.setBackground(color);
      }
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
        registerNavigateToSourceShortcutOnComponent((JBTable)c, component);
      }
    }
  }

  protected void registerNavigateToSourceShortcutOnComponent(@NotNull final JBTable c, JComponent component) {
    AnAction anAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        navigateToSelectedUsage(c);
      }
    };
    anAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), component, myDisposable);
  }
}

