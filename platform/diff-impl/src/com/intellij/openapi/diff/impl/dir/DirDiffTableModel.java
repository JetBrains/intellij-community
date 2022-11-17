// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.intellij.CommonBundle;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.diff.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.dir.actions.popup.WarnOnDeletion;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.TableUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffTableModel extends AbstractTableModel implements DirDiffModel, Disposable {
  private static final Logger LOG = Logger.getInstance(DirDiffTableModel.class);

  public static final Key<JBLoadingPanel> DECORATOR_KEY = Key.create("DIFF_TABLE_DECORATOR");

  public enum ColumnType {OPERATION, NAME, SIZE, DATE}

  public static final String EMPTY_STRING = StringUtil.repeatSymbol(' ', 50);

  @Nullable private final Project myProject;
  private final DirDiffSettings mySettings;

  protected DiffElement mySource;
  protected DiffElement myTarget;
  private DTree myTree;
  private final List<DirDiffElementImpl> myElements = new ArrayList<>();
  private final AtomicBoolean myUpdating = new AtomicBoolean(false);
  private JBTable myTable;
  private final AtomicReference<@Nls String> text = new AtomicReference<>(prepareText(""));
  private volatile Updater myUpdater;
  private final List<DirDiffModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private TableSelectionConfig mySelectionConfig;
  /** directory path -> map from name of source element name to name of target element which is manually specified as replacement for that source */
  private final Map<String, BiMap<String, String>> mySourceToReplacingTarget = HashBiMap.create();

  private DirDiffPanel myPanel;
  private volatile boolean myDisposed;

  public DirDiffTableModel(@Nullable Project project, DiffElement<?> source, DiffElement<?> target, DirDiffSettings settings) {
    myProject = project;
    mySettings = settings;
    mySource = source;
    myTarget = target;
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
    return !myDisposed && mySettings.enableOperations && mySource.isOperationsEnabled() && myTarget.isOperationsEnabled();
  }

  @Override
  public List<DirDiffElementImpl> getElements() {
    return myElements;
  }

  public void setReplacement(DirDiffElementImpl source, @Nullable DirDiffElementImpl target) {
    setReplacement(source.getParentNode().getPath(), source.getSourceName(), target == null ? null : target.getTargetName());
  }

  public void setReplacement(@NotNull String path, @Nullable String sourceName, @Nullable String targetName) {
    BiMap<String, String> map = mySourceToReplacingTarget.computeIfAbsent(path, (p) -> HashBiMap.create());
    if (targetName != null) {
      map.forcePut(sourceName, targetName);
    }
    else {
      map.remove(sourceName);
    }
  }

  public String getReplacementName(DirDiffElementImpl source) {
    BiMap<String, String> map = mySourceToReplacingTarget.get(source.getParentNode().getPath());
    return map != null ? map.get(source.getSourceName()) : null;
  }

  @Nls
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
    return DiffBundle.message("label.dirdiff.loading.file", right);
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

  @Override
  public void reloadModel(boolean userForcedRefresh) {
    fireUpdateStarted();
    myUpdating.set(true);
    myTable.getEmptyText().setText(StatusText.getDefaultEmptyText());
    JBLoadingPanel loadingPanel = getLoadingPanel();
    loadingPanel.startLoading();

    ModalityState modalityState = ModalityState.current();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      EmptyProgressIndicator indicator = new EmptyProgressIndicator(modalityState);
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        try {
          if (myDisposed) return;
          startAndSetUpdater(new Updater(loadingPanel, 100));
          text.set(CommonBundle.getLoadingTreeNodeText());
          myTree = new DTree(null, "", true);
          mySource.refresh(userForcedRefresh);
          myTarget.refresh(userForcedRefresh);
          scan(mySource, myTree, true);
          scan(myTarget, myTree, false);
        }
        catch (IOException e) {
          LOG.warn(e);
          reportException(DiffBundle.message("refresh.failed.message", StringUtil.decapitalize(e.getLocalizedMessage())));
        }
        finally {
          if (myTree != null) {
            myTree.setSource(mySource);
            myTree.setTarget(myTarget);
            myTree.update(mySettings);
            ApplicationManager.getApplication().invokeLater(this::fireUpdateFinished, ModalityState.any());
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
      fireUpdateStarted();
      myTree = new DTree(null, "", true);
      mySource.refresh(true);
      myTarget.refresh(true);
      scan(mySource, myTree, true);
      scan(myTarget, myTree, false);
    }
    catch (final IOException e) {
      LOG.warn(e);
      reportException(DiffBundle.message("refresh.failed.message", StringUtil.decapitalize(e.getLocalizedMessage())));
    }
    finally {
      myTree.setSource(mySource);
      myTree.setTarget(myTarget);
      myTree.update(mySettings);

      ArrayList<DirDiffElementImpl> elements = new ArrayList<>();
      fillElements(myTree, elements);
      myElements.clear();
      myElements.addAll(elements);

      myUpdating.set(false);
      fireUpdateFinished();
    }
  }

  protected void reportException(@Nullable @Nls String htmlContent) {
    if (myDisposed || htmlContent == null) return;
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
    ApplicationManager.getApplication().invokeLater(balloonShower, o ->
      myProject != null && !myProject.isDefault() && !myProject.isOpen());
  }

  private JBLoadingPanel getLoadingPanel() {
    return ComponentUtil.getClientProperty(myTable, DECORATOR_KEY);
  }

  @Override
  public void applySettings() {
    if (!myUpdating.get()) myUpdating.set(true);
    JBLoadingPanel loadingPanel = getLoadingPanel();
    if (!loadingPanel.isLoading()) {
      loadingPanel.startLoading();
      if (myUpdater == null) {
        startAndSetUpdater(new Updater(loadingPanel, 100));
      }
    }
    Application app = ApplicationManager.getApplication();
    app.executeOnPooledThread(() -> {
      if (myDisposed) return;
      myTree.updateVisibility(mySettings);
      ArrayList<DirDiffElementImpl> elements = new ArrayList<>();
      fillElements(myTree, elements);
      Runnable uiThread = () -> {
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
        }
        else {
          mySelectionConfig.restore();
        }
        myPanel.update(true);
      };
      if (myProject == null || myProject.isDefault()) {
        SwingUtilities.invokeLater(uiThread);
      }
      else {
        app.invokeLater(uiThread, ModalityState.any());
      }
    });
  }

  private void fillElements(DTree tree, List<? super DirDiffElementImpl> elements) {
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

  private void scan(DiffElement<?> element, DTree root, boolean source) throws IOException {
    if (!myUpdating.get()) return;
    if (element.isContainer()) {
      text.set(prepareText(element.getPath()));
      final DiffElement<?>[] children = element.getChildren();
      for (DiffElement<?> child : children) {
        if (!myUpdating.get()) return;
        text.set(prepareText(child.getPath()));
        BiMap<String, String> replacing = mySourceToReplacingTarget.get(root.getPath());
        String replacementName = replacing != null ? source ? replacing.get(child.getName()) : replacing.inverse().get(child.getName()) : null;
        final DTree el = root.addChild(child, source, replacementName);
        scan(child, el, source);
      }
    }
  }

  @NlsContexts.DialogTitle
  public String getTitle() {
    if (myDisposed) return DiffBundle.message("diff.files.dialog.title");
    if (mySource instanceof VirtualFileDiffElement &&
        myTarget instanceof VirtualFileDiffElement) {
      VirtualFile srcFile = ((VirtualFileDiffElement)mySource).getValue();
      VirtualFile trgFile = ((VirtualFileDiffElement)myTarget).getValue();
      return DiffRequestFactory.getInstance().getTitle(srcFile, trgFile);
    }
    return IdeBundle.message("diff.dialog.title", mySource.getPresentablePath(), myTarget.getPresentablePath());
  }

  @Nullable
  public DirDiffElementImpl getElementAt(int index) {
    return 0 <= index && index < myElements.size() ? myElements.get(index) : null;
  }

  @Override
  public DiffElement getSourceDir() {
    return mySource;
  }

  @Override
  public DiffElement getTargetDir() {
    return myTarget;
  }

  @Override
  public void setSourceDir(DiffElement src) {
    mySource = src;
  }

  @Override
  public void setTargetDir(DiffElement trg) {
    myTarget = trg;
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

      final ColumnType columnType = getColumnType(columnIndex);
      boolean isSrc = columnIndex < getColumnCount() / 2;
      if (columnType == ColumnType.NAME) {
        return isSrc ? element.getSourcePresentableName() : element.getTargetPresentableName();
      }
      else if (columnType == ColumnType.SIZE) {
        return isSrc ? element.getSourceSize() : element.getTargetSize();
      }
      else if (columnType == ColumnType.DATE) {
        return isSrc ? element.getSourceModificationDate() : element.getTargetModificationDate();
      }
      return "";
    }
    catch (Exception e) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        myElements.clear();
        fireTableDataChanged();
        myTable.getEmptyText().setText(DiffBundle.message("data.has.been.changed.externally.reloading.data"));
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

  @NotNull
  public ColumnType getColumnType(int column) {
    final int count = (getColumnCount() - 1) / 2;
    if (column == count) return ColumnType.OPERATION;
    if (column > count) {
      column = getColumnCount() - 1 - column;
    }
    return switch (column) {
      case 0 -> ColumnType.NAME;
      case 1 -> mySettings.showSize ? ColumnType.SIZE : ColumnType.DATE;
      case 2 -> ColumnType.DATE;
      default -> throw new IllegalArgumentException(String.valueOf(column));
    };
  }

  @Override
  public String getColumnName(int column) {
    ColumnType type = getColumnType(column);
    return switch (type) {
      case OPERATION -> "*"; // NON-NLS
      case NAME -> DiffBundle.message("column.dirdiff.name");
      case SIZE -> DiffBundle.message("column.dirdiff.size");
      case DATE -> DiffBundle.message("column.dirdiff.date");
    };
  }

  @Nullable
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
    mySource = null;
    myTarget = null;
    myTree = null;
  }

  @Override
  public DirDiffSettings getSettings() {
    return mySettings;
  }

  public void performCopyTo(final DirDiffElementImpl element) {
    DiffElement<?> source = element.getSource();
    if (source != null) {
      String path = element.getParentNode().getPath();

      if (source instanceof AsyncDiffElement) {
        ((AsyncDiffElement)source).copyToAsync(myTarget, element.getTarget(), path)
          .onError(error -> reportException(error == null ? null : error.getMessage()))
          .onSuccess(newElement -> refreshAfterCopyTo(element, newElement));
      }
      else {
        WriteAction.run(() -> {
          DiffElement<?> diffElement = source.copyTo(myTarget, path);
          refreshElementAfterCopyTo(diffElement, element);
        });
      }
    }
  }

  protected void refreshAfterCopyTo(DirDiffElementImpl element, DiffElement newElement) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myDisposed) return;
    if (newElement == null && element.getTarget() != null) {
      final int row = myElements.indexOf(element);
      element.updateTargetData();
      fireTableRowsUpdated(row, row);
    }
    refreshElementAfterCopyTo(newElement, element);
  }

  protected void refreshElementAfterCopyTo(DiffElement<?> newElement, DirDiffElementImpl element) {
    if (newElement != null) {
      DTree node = element.getNode();
      node.setType(DiffType.EQUAL);
      node.setTarget(newElement);

      int row = myElements.indexOf(element);
      if (getSettings().showEqual) {
        element.updateSourceFromTarget(newElement);
        fireTableRowsUpdated(row, row);
      }
      else {
        removeElement(element, false);
      }
    }
  }

  public void performCopyFrom(@NotNull DirDiffElementImpl element) {
    DiffElement<?> target = element.getTarget();
    if (target != null) {
      String path = element.getParentNode().getPath();

      if (target instanceof AsyncDiffElement) {
        ((AsyncDiffElement)target).copyToAsync(mySource, element.getSource(), path)
          .onError(error -> reportException(error == null ? null : error.getMessage()))
          .onSuccess(newElement -> refreshAfterCopyFrom(element, newElement));
      }
      else {
        WriteAction.run(() -> {
          DiffElement<?> diffElement = target.copyTo(mySource, path);
          refreshElementAfterCopyFrom(element, diffElement);
        });
      }
    }
  }

  protected void refreshAfterCopyFrom(@NotNull DirDiffElementImpl element, DiffElement newElement) {
    if (myDisposed) return;
    ApplicationManager.getApplication().assertIsDispatchThread();
    refreshElementAfterCopyFrom(element, newElement);
  }

  protected void refreshElementAfterCopyFrom(DirDiffElementImpl element, DiffElement<?> newElement) {
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

      if (row > 0 && row == myElements.size() && myElements.get(row - 1).isSeparator() ||
          row != myElements.size() && myElements.get(row).isSeparator() && row > 0 && myElements.get(row - 1).isSeparator()) {
        DirDiffElementImpl el = myElements.get(row - 1);
        if (removeFromTree) {
          el.getParentNode().remove(el.getNode());
        }
        myElements.remove(row - 1);
        start = row - 1;
      }
      fireTableRowsDeleted(start, row);
    }
  }

  public void performDelete(@NotNull DirDiffElementImpl element) {
    DiffElement<?> source = element.getSource();
    DiffElement<?> target = element.getTarget();
    LOG.assertTrue(source == null || target == null);
    if (source instanceof AsyncDiffElement || target instanceof AsyncDiffElement) {
      ((AsyncDiffElement)(source != null ? source : target)).deleteAsync()
        .onError(error -> reportException(error != null ? error.getMessage() : null))
        .onSuccess(result -> {
          if (!myDisposed && myElements.contains(element)) {
            removeElement(element, true);
          }
        });
    }
    else {
      if (myElements.contains(element)) {
        removeElement(element, true);
      }
      WriteAction.run(
        () -> (source != null ? source : target).delete());
    }
  }

  public void synchronizeSelected() {
    List<DirDiffElementImpl> selectedElements = getSelectedElements();
    if (!checkCanDelete(selectedElements)) {
      return;
    }
    rememberSelection();
    sync(selectedElements);
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
    sync(elements);
    selectFirstRow();
  }

  protected void sync(List<DirDiffElementImpl> elements) {
    for (DirDiffElementImpl element : elements) {
      syncElement(element);
    }
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
    return MessageDialogBuilder.yesNo(DiffBundle.message("confirm.delete"),
                                      DiffBundle.message("delete.0.items", count))
      .yesText(CommonBundle.message("button.delete"))
      .noText(CommonBundle.getCancelButtonText())
      .doNotAsk(new DoNotAskOption() {
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
          return DiffBundle.message("do.not.ask.me.again");
        }
      })
      .ask(myProject);
  }

  protected void syncElement(DirDiffElementImpl element) {
    final DirDiffOperation operation = element.getOperation();
    if (operation == null) return;
    switch (operation) {
      case COPY_TO:
        performCopyTo(element);
        break;
      case COPY_FROM:
        performCopyFrom(element);
        break;
      case DELETE:
        performDelete(element);
        break;
      case MERGE:
      case EQUAL:
      case NONE:
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
        startAndSetUpdater(new Updater(myLoadingPanel, mySleep));
      } else {
        myUpdater = null;
      }
    }
  }

  private void startAndSetUpdater(Updater updater) {
    updater.start();
    myUpdater = updater;
  }

  public void rememberSelection() {
    mySelectionConfig = new TableSelectionConfig();
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
