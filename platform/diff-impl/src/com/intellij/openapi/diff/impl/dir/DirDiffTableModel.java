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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.diff.*;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.dir.actions.popup.WarnOnDeletion;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.TableUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffTableModel extends AbstractTableModel implements DirDiffModel, Disposable {
  private static final Logger LOG = Logger.getInstance("#"+DirDiffTableModel.class.getName());
  public static final String COLUMN_NAME = "Name";
  public static final String COLUMN_SIZE = "Size";
  public static final String COLUMN_DATE = "Date";
  private final Project myProject;
  private final DirDiffSettings mySettings;
  private DiffElement mySrc;
  private DiffElement myTrg;
  private DTree myTree;
  private final List<DirDiffElementImpl> myElements = new ArrayList<>();
  private final AtomicBoolean myUpdating = new AtomicBoolean(false);
  private JBTable myTable;
  public String DECORATOR = "DIFF_TABLE_DECORATOR";
  public final AtomicReference<String> text = new AtomicReference<>(prepareText(""));
  private Updater myUpdater;
  private List<DirDiffModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private TableSelectionConfig mySelectionConfig;
  private String myStatus = null;
  public static final String EMPTY_STRING = "                                                  ";
  private DirDiffPanel myPanel;
  private volatile boolean myDisposed;

  public DirDiffTableModel(@NotNull Project project, DiffElement src, DiffElement trg, DirDiffSettings settings) {
    UsageTrigger.trigger("diff.DirDiffTableModel");
    myProject = project;
    mySettings = settings;
    mySrc = src;
    myTrg = trg;
  }

  public void stopUpdating() {
    if (myUpdating.get()) {
      myUpdating.set(false);
    }
  }

  public void applyRemove() {
    final List<DirDiffElementImpl> selectedElements = getSelectedElements();
    myUpdating.set(true);
    final Iterator<DirDiffElementImpl> i = myElements.iterator();
    while(i.hasNext()) {
      final DiffType type = i.next().getType();
      switch (type) {
        case SOURCE:
          if (!mySettings.showNewOnSource) i.remove();
          break;
        case TARGET:
          if (!mySettings.showNewOnTarget) i.remove();
          break;
        case SEPARATOR:
          break;
        case CHANGED:
          if (!mySettings.showDifferent) i.remove();
          break;
        case EQUAL:
          if (!mySettings.showEqual) i.remove();
          break;
        case ERROR:
      }
    }

    boolean sep = true;
    for (int j = myElements.size() - 1; j >= 0; j--) {
      if (myElements.get(j).isSeparator()) {
        if (sep) {
          myElements.remove(j);
        }
        else {
          sep = true;
        }
      }
      else {
        sep = false;
      }
    }
    fireTableDataChanged();
    myUpdating.set(false);
    int index;
    if (!selectedElements.isEmpty() && (index = myElements.indexOf(selectedElements.get(0))) != -1) {
      myTable.getSelectionModel().setSelectionInterval(index, index);
      TableUtil.scrollSelectionToVisible(myTable);
    }
    else {
      selectFirstRow();
    }
    myPanel.update(true);
  }

  public void selectFirstRow() {
    if (myElements.size() > 0) {
      int row = myElements.get(0).isSeparator() ? 1 : 0;
      if (row < myTable.getRowCount()) {
        myTable.getSelectionModel().setSelectionInterval(row, row);
        TableUtil.scrollSelectionToVisible(myTable);
      }
    }
  }

  public void setPanel(DirDiffPanel panel) {
    myPanel = panel;
  }

  public void updateFromUI() {
    getSettings().setFilter(myPanel.getFilter());
    myPanel.update(false);
  }

  public boolean isOperationsEnabled() {
    return !myDisposed && mySrc.isOperationsEnabled() && myTrg.isOperationsEnabled();
  }

  public List<DirDiffElementImpl> getElements() {
    return myElements;
  }

  private static String prepareText(String text) {
    final int LEN = EMPTY_STRING.length();
    String right;
    if (text == null) {
      right = EMPTY_STRING;
    }
    else if (text.length() == LEN) {
      right = text;
    }
    else if (text.length() < LEN) {
      right = text + EMPTY_STRING.substring(0, LEN - text.length());
    }
    else {
      right = "..." + text.substring(text.length() - LEN + 2);
    }
    return "Loading... " + right;
  }

  void fireUpdateStarted() {
    for (DirDiffModelListener listener : myListeners) {
      listener.updateStarted();
    }
  }

  void fireUpdateFinished() {
    for (DirDiffModelListener listener : myListeners) {
      listener.updateFinished();
    }
  }

  void addModelListener(DirDiffModelListener listener) {
    myListeners.add(listener);
  }

  public void reloadModel(final boolean userForcedRefresh) {
    myUpdating.set(true);
    myTable.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    final JBLoadingPanel loadingPanel = getLoadingPanel();
    loadingPanel.startLoading();

    final ModalityState modalityState = ModalityState.current();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      EmptyProgressIndicator indicator = new EmptyProgressIndicator(modalityState);
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        try {
          if (myDisposed) return;
          myUpdater = new Updater(loadingPanel, 100);
          myUpdater.start();
          text.set("Loading...");
          myTree = new DTree(null, "", true);
          mySrc.refresh(userForcedRefresh);
          myTrg.refresh(userForcedRefresh);
          scan(mySrc, myTree, true);
          scan(myTrg, myTree, false);
        }
        catch (final IOException e) {
          LOG.warn(e);
          reportException(VcsBundle.message("refresh.failed.message", StringUtil.decapitalize(e.getLocalizedMessage())));
        }
        finally {
          if (myTree != null) {
            myTree.setSource(mySrc);
            myTree.setTarget(myTrg);
            myTree.update(mySettings);
            applySettings();
          }
        }
      }, indicator);
    });
  }

  // todo move to headless model implementation
  public void reloadModelSynchronously() {
    myUpdating.set(true);

    try {
      myTree = new DTree(null, "", true);
      mySrc.refresh(true);
      myTrg.refresh(true);
      scan(mySrc, myTree, true);
      scan(myTrg, myTree, false);
    }
    catch (final IOException e) {
      LOG.warn(e);
      reportException(VcsBundle.message("refresh.failed.message", StringUtil.decapitalize(e.getLocalizedMessage())));
    }
    finally {
      myTree.setSource(mySrc);
      myTree.setTarget(myTrg);
      myTree.update(mySettings);

      ArrayList<DirDiffElementImpl> elements = new ArrayList<>();
      fillElements(myTree, elements);
      myElements.clear();
      myElements.addAll(elements);

      myUpdating.set(false);
    }
  }

  private void reportException(final String htmlContent) {
    Runnable balloonShower = () -> {
      Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(htmlContent, MessageType.WARNING, null).
        setShowCallout(false).setHideOnClickOutside(true).setHideOnAction(true).setHideOnFrameResize(true).setHideOnKeyOutside(true).
        createBalloon();
      final Rectangle rect = myPanel.getPanel().getBounds();
      final Point p = new Point(rect.x + rect.width - 100, rect.y + 50);
      final RelativePoint point = new RelativePoint(myPanel.getPanel(), p);
      balloon.show(point, Balloon.Position.below);
      Disposer.register(myProject != null ? myProject : ApplicationManager.getApplication(), balloon);
    };
    ApplicationManager.getApplication().invokeLater(balloonShower, o -> !(myProject == null || myProject.isDefault()) && ((!myProject.isOpen()) || myProject.isDisposed())
    );
  }

  private JBLoadingPanel getLoadingPanel() {
    return (JBLoadingPanel)myTable.getClientProperty(DECORATOR);
  }

  public void applySettings() {
    if (! myUpdating.get()) myUpdating.set(true);
    final JBLoadingPanel loadingPanel = getLoadingPanel();
    if (!loadingPanel.isLoading()) {
      loadingPanel.startLoading();
      if (myUpdater == null) {
        myUpdater = new Updater(loadingPanel, 100);
        myUpdater.start();
      }
    }
    final Application app = ApplicationManager.getApplication();
    app.executeOnPooledThread(() -> {
      if (myDisposed) return;
      myTree.updateVisibility(mySettings);
      final ArrayList<DirDiffElementImpl> elements = new ArrayList<>();
      fillElements(myTree, elements);
      final Runnable uiThread = () -> {
        if (myDisposed) return;
        clear();
        myElements.addAll(elements);
        myUpdating.set(false);
        fireTableDataChanged();
        this.text.set("");
        if (loadingPanel.isLoading()) {
          loadingPanel.stopLoading();
        }
        if (mySelectionConfig == null) {
          selectFirstRow();
        } else {
          mySelectionConfig.restore();
        }
        myPanel.update(true);
      };
      if (myProject == null || myProject.isDefault()) {
        SwingUtilities.invokeLater(uiThread);
      } else {
        app.invokeLater(uiThread, ModalityState.any());
      }
    });
  }

  private void fillElements(DTree tree, List<DirDiffElementImpl> elements) {
    if (!myUpdating.get()) return;
    boolean separatorAdded = tree.getParent() == null;
    text.set(prepareText(tree.getPath()));
    for (DTree child : tree.getChildren()) {
      if (!myUpdating.get()) return;
      if (!child.isContainer()) {
        if (child.isVisible()) {
          if (!separatorAdded) {
            elements.add(DirDiffElementImpl.createDirElement(tree, tree.getSource(), tree.getTarget(), tree.getPath()));
            separatorAdded = true;
          }
          final DiffType type = child.getType();
          if (type != null) {
            switch (type) {
              case SOURCE:
                elements.add(DirDiffElementImpl.createSourceOnly(child, child.getSource()));
                break;
              case TARGET:
                elements.add(DirDiffElementImpl.createTargetOnly(child, child.getTarget()));
                break;
              case CHANGED:
                elements.add(DirDiffElementImpl.createChange(child, child.getSource(), child.getTarget(), mySettings.customSourceChooser));
                break;
              case EQUAL:
                elements.add(DirDiffElementImpl.createEqual(child, child.getSource(), child.getTarget()));
                break;
              case ERROR:
                elements.add(DirDiffElementImpl.createError(child, child.getSource(), child.getTarget()));
              case SEPARATOR:
                break;
            }
          } else {
            LOG.error(String.format("Element's type is null [Name: %s, Container: %s, Source: %s, Target: %s] ",
                                   child.getName(), child.isContainer(), child.getSource(), child.getTarget()));
          }
        }
      } else {
        fillElements(child, elements);
      }
    }
  }

  public void clear() {
    if (!myElements.isEmpty()) {
      final int size = myElements.size();
      myElements.clear();
      fireTableRowsDeleted(0, size - 1);
    }
  }

  private void scan(DiffElement element, DTree root, boolean source) throws IOException {
    if (!myUpdating.get()) return;
    if (element.isContainer()) {
      text.set(prepareText(element.getPath()));
      final DiffElement[] children = element.getChildren();
      for (DiffElement child : children) {
        if (!myUpdating.get()) return;
        final DTree el = root.addChild(child, source);
        scan(child, el, source);
      }
    }
  }

  public String getTitle() {
    if (myDisposed) return "";
    return IdeBundle.message("diff.dialog.title", mySrc.getPresentablePath(), myTrg.getPresentablePath());
  }

  @Nullable
  public DirDiffElementImpl getElementAt(int index) {
    return 0 <= index && index < myElements.size() ? myElements.get(index) : null;
  }

  public DiffElement getSourceDir() {
    return mySrc;
  }

  public DiffElement getTargetDir() {
    return myTrg;
  }

  public void setSourceDir(DiffElement src) {
    mySrc = src;
  }

  public void setTargetDir(DiffElement trg) {
    myTrg = trg;
  }

  @Override
  public int getRowCount() {
    return myElements.size();
  }

  @Override
  public int getColumnCount() {
    int count = 3;
    if (mySettings.showDate) count += 2;
    if (mySettings.showSize) count += 2;
    return count;
  }

  public JBTable getTable() {
    return myTable;
  }

  public void setTable(JBTable table) {
    myTable = table;
  }

  @Nullable
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    try {
      final DirDiffElementImpl element = myElements.get(rowIndex);
      if (element.isSeparator()) {
        return columnIndex == 0 ? element.getName() : null;
      }

      final String name = getColumnName(columnIndex);
      boolean isSrc = columnIndex < getColumnCount() / 2;
      if (name.equals(COLUMN_NAME)) {
        return isSrc ? element.getSourceName() : element.getTargetName();
      } else if (name.equals(COLUMN_SIZE)) {
        return isSrc ? element.getSourceSize() : element.getTargetSize();
      } else  if (name.equals(COLUMN_DATE)) {
        return isSrc ? element.getSourceModificationDate() : element.getTargetModificationDate();
      }
      return "";
    }
    catch (Exception e) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        myElements.clear();
        fireTableDataChanged();
        myTable.getEmptyText().setText("Data has been changed externally. Reloading data...");
        reloadModel(true);
        myTable.repaint();
      });
      return "";
    }
  }

  public List<DirDiffElementImpl> getSelectedElements() {
    final int[] rows = myTable.getSelectedRows();
    final ArrayList<DirDiffElementImpl> elements = new ArrayList<>();
    for (int row : rows) {
      final DirDiffElementImpl element = getElementAt(row);
      if (element == null || element.isSeparator()) continue;
      elements.add(element);
    }
    return elements;
  }

  @Override
  public String getColumnName(int column) {
    final int count = (getColumnCount() - 1) / 2;
    if (column == count) return "*";
    if (column > count) {
      column = getColumnCount() - 1 - column;
    }
    switch (column) {
      case 0: return COLUMN_NAME;
      case 1: return mySettings.showSize ? COLUMN_SIZE : COLUMN_DATE;
      case 2: return COLUMN_DATE;
    }
    return "";
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isShowEqual() {
    return mySettings.showEqual;
  }

  public void setShowEqual(boolean show) {
    mySettings.showEqual = show;
  }

  public boolean isShowDifferent() {
    return mySettings.showDifferent;
  }

  public void setShowDifferent(boolean show) {
    mySettings.showDifferent = show;
  }

  public boolean isShowNewOnSource() {
    return mySettings.showNewOnSource;
  }

  public void setShowNewOnSource(boolean show) {
    mySettings.showNewOnSource = show;
  }

  public boolean isShowNewOnTarget() {
    return mySettings.showNewOnTarget;
  }

  public void setShowNewOnTarget(boolean show) {
    mySettings.showNewOnTarget = show;
  }

  public boolean isUpdating() {
    return myUpdating.get();
  }

  public DirDiffSettings.CompareMode getCompareMode() {
    return mySettings.compareMode;
  }

  public void setCompareMode(DirDiffSettings.CompareMode mode) {
    mySettings.compareMode = mode;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myListeners.clear();
    myElements.clear();
    mySrc = null;
    myTrg = null;
    myTree = null;
  }

  public DirDiffSettings getSettings() {
    return mySettings;
  }

  public void performCopyTo(final DirDiffElementImpl element) {
    final DiffElement<?> source = element.getSource();
    if (source != null) {
      final String path = element.getParentNode().getPath();

      if (source instanceof BackgroundOperatingDiffElement) {
        final Ref<String> errorMessage = new Ref<>();
        final Ref<DiffElement> diff = new Ref<>();
        Runnable onFinish = () -> {
          ApplicationManager.getApplication().assertIsDispatchThread();
          if (!myDisposed) {
            DiffElement newElement = diff.get();
            if (newElement == null && element.getTarget() != null) {
              final int row = myElements.indexOf(element);
              element.updateTargetData();
              fireTableRowsUpdated(row, row);
            }
            refreshElementAfterCopyTo(newElement, element);
            if (!errorMessage.isNull()) {
              reportException(errorMessage.get());
            }
          }
        };
        ((BackgroundOperatingDiffElement)source).copyTo(myTrg, errorMessage, diff, onFinish, element.getTarget(), path);
      }
      else {
        final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
        try {
          final DiffElement<?> diffElement = source.copyTo(myTrg, path);
          refreshElementAfterCopyTo(diffElement, element);
        }
        finally {
          token.finish();
        }
      }
    }
  }

  private void refreshElementAfterCopyTo(DiffElement newElement, DirDiffElementImpl element) {
    if (newElement != null) {
      final DTree node = element.getNode();
      node.setType(DiffType.EQUAL);
      node.setTarget(newElement);

      final int row = myElements.indexOf(element);
      if (getSettings().showEqual) {
        element.updateSourceFromTarget(newElement);
        fireTableRowsUpdated(row, row);
      }
      else {
        removeElement(element, false);
      }
    }
  }

  public void performCopyFrom(final DirDiffElementImpl element) {
    final DiffElement<?> target = element.getTarget();
    if (target != null) {
      final String path = element.getParentNode().getPath();

      if (target instanceof BackgroundOperatingDiffElement) {
        final Ref<String> errorMessage = new Ref<>();
        final Ref<DiffElement> diff = new Ref<>();
        Runnable onFinish = () -> {
          ApplicationManager.getApplication().assertIsDispatchThread();
          if (!myDisposed) {
            refreshElementAfterCopyFrom(element, diff.get());
            if (!errorMessage.isNull()) {
              reportException(errorMessage.get());
            }
          }
        };
        ((BackgroundOperatingDiffElement)target).copyTo(mySrc, errorMessage, diff, onFinish, element.getSource(), path);
      }
      else {
        final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
        try {
          final DiffElement<?> diffElement = target.copyTo(mySrc, path);
          refreshElementAfterCopyFrom(element, diffElement);
        }
        finally {
          token.finish();
        }
      }
    }
  }

  private void refreshElementAfterCopyFrom(DirDiffElementImpl element, DiffElement newElement) {
    if (newElement != null) {
      final DTree node = element.getNode();
      node.setType(DiffType.EQUAL);
      node.setSource(newElement);

      final int row = myElements.indexOf(element);
      if (getSettings().showEqual) {
        element.updateTargetFromSource(newElement);
        fireTableRowsUpdated(row, row);
      }
      else {
        removeElement(element, false);
      }
    }
  }

  private void removeElement(DirDiffElementImpl element, boolean removeFromTree) {
    int row = myElements.indexOf(element);
    if (row != -1) {
      final DTree node = element.getNode();
      if (removeFromTree) {
        final DTree parentNode = element.getParentNode();
        parentNode.remove(node);
      }
      myElements.remove(row);
      int start = row;

      if (row > 0 && row == myElements.size() && myElements.get(row - 1).isSeparator()) {
        final DirDiffElementImpl el = myElements.get(row - 1);
        if (removeFromTree) {
          el.getParentNode().remove(el.getNode());
        }
        myElements.remove(row - 1);
        start = row - 1;
        }
        else if (row != myElements.size() && myElements.get(row).isSeparator() && row > 0 && myElements.get(row - 1).isSeparator()) {
          final DirDiffElementImpl el = myElements.get(row - 1);
          if (removeFromTree) {
            el.getParentNode().remove(el.getNode());
          }
          myElements.remove(row - 1);
          start = row - 1;
        }
        fireTableRowsDeleted(start, row);
      }
  }

  public void performDelete(final DirDiffElementImpl element) {
    final DiffElement source = element.getSource();
    final DiffElement target = element.getTarget();
    LOG.assertTrue(source == null || target == null);
    if (source instanceof BackgroundOperatingDiffElement || target instanceof BackgroundOperatingDiffElement) {
      final Ref<String> errorMessage = new Ref<>();
      Runnable onFinish = () -> {
        if (!myDisposed) {
          if (!errorMessage.isNull()) {
            reportException(errorMessage.get());
          }
          else {
            if (myElements.indexOf(element) != -1) {
              removeElement(element, true);
            }
          }
        }
      };
      if (source != null) {
        ((BackgroundOperatingDiffElement)source).delete(errorMessage, onFinish);
      }
      else {
        ((BackgroundOperatingDiffElement)target).delete(errorMessage, onFinish);
      }
    }
    else {
      if (myElements.indexOf(element) != -1) {
        removeElement(element, true);
      }
      final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
      try {
        if (source != null) {
          source.delete();
        }
        if (target != null) {
          target.delete();
        }
      }
      finally {
        token.finish();
      }
    }
  }

  public void synchronizeSelected() {
    List<DirDiffElementImpl> selectedElements = getSelectedElements();
    if (!checkCanDelete(selectedElements)) {
      return;
    }
    rememberSelection();
    for (DirDiffElementImpl element : selectedElements) {
      syncElement(element);
    }
    restoreSelection();
 }

  private void restoreSelection() {
    if (mySelectionConfig != null) {
      mySelectionConfig.restore();
    }
  }

  public void synchronizeAll() {
    List<DirDiffElementImpl> elements = new ArrayList<>(myElements);
    if (!checkCanDelete(elements)) {
      return;
    }
    for (DirDiffElementImpl element : elements) {
      syncElement(element);
    }
    selectFirstRow();
  }

  private boolean checkCanDelete(List<DirDiffElementImpl> elements) {
    if (WarnOnDeletion.isWarnWhenDeleteItems()) {
      int count = 0;
      for (DirDiffElementImpl element : elements) {
        if (element.getOperation() == DirDiffOperation.DELETE) {
          count++;
        }
      }
      if (count > 0) {
        if (!confirmDeletion(count)) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean confirmDeletion(int count) {
    return MessageDialogBuilder.yesNo("Confirm Delete", "Delete " + count + " items?").project(myProject).yesText("Delete").noText(CommonBundle.message("button.cancel")).doNotAsk(
      new DialogWrapper.DoNotAskOption() {
        @Override
        public boolean isToBeShown() {
          return WarnOnDeletion.isWarnWhenDeleteItems();
        }

        @Override
        public void setToBeShown(boolean value, int exitCode) {
          WarnOnDeletion.setWarnWhenDeleteItems(value);
        }

        @Override
        public boolean canBeHidden() {
          return true;
        }

        @Override
        public boolean shouldSaveOptionsOnCancel() {
          return true;
        }

        @NotNull
        @Override
        public String getDoNotShowMessage() {
          return "Do not ask me again";
        }
      }).show() == Messages.YES;
  }

  private void syncElement(DirDiffElementImpl element) {
    final DirDiffOperation operation = element.getOperation();
    if (operation == null) return;
    switch (operation) {
      case COPY_TO:
        performCopyTo(element);
        break;
      case COPY_FROM:
        performCopyFrom(element);
        break;
      case MERGE:
        break;
      case EQUAL:
        break;
      case NONE:
        break;
      case DELETE:
        performDelete(element);
        break;
    }
  }

  class Updater extends Thread {
    private final JBLoadingPanel myLoadingPanel;
    private final int mySleep;

    Updater(JBLoadingPanel loadingPanel, int sleep) {
      super("Loading Updater");
      myLoadingPanel = loadingPanel;
      mySleep = sleep;
    }

    @Override
    public void run() {
      if (!myDisposed && myLoadingPanel.isLoading()) {
        TimeoutUtil.sleep(mySleep);
        ApplicationManager.getApplication().invokeLater(() -> {
          final String s = text.get();
          if (s != null && myLoadingPanel.isLoading()) {
            myLoadingPanel.setLoadingText(s);
          }
        }, ModalityState.stateForComponent(myLoadingPanel));
        myUpdater = new Updater(myLoadingPanel, mySleep);
        myUpdater.start();
      } else {
        myUpdater = null;
      }
    }
  }

  public void rememberSelection() {
    mySelectionConfig = new TableSelectionConfig();
  }

  public void clearWithMessage(String message) {
    myTable.getEmptyText().setText(message);
    myElements.clear();
    fireTableDataChanged();
  }

  public class TableSelectionConfig {
    private final int selectedRow;
    private final int rowCount;
    TableSelectionConfig() {
      selectedRow = myTable.getSelectedRow();
      rowCount = myTable.getRowCount();
    }

    void restore() {
      final int newRowCount = myTable.getRowCount();
      if (newRowCount == 0) return;

      int row = Math.min(newRowCount < rowCount ? selectedRow : selectedRow + 1, newRowCount - 1);
      final DirDiffElementImpl element = getElementAt(row);
      if (element != null && element.isSeparator()) {
        if (getElementAt(row +1) != null) {
          row += 1;
        } else {
          row -= 1;
        }
      }
      final DirDiffElementImpl el = getElementAt(row);
      row = el == null || el.isSeparator() ? 0 : row;
      myTable.getSelectionModel().setSelectionInterval(row, row);
      TableUtil.scrollSelectionToVisible(myTable);
    }
  }
}
