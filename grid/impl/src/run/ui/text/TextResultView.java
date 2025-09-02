package com.intellij.database.run.ui.text;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.util.Out;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.intellij.database.extractors.ExtractionConfigKt.builder;

/**
 * @author Liudmila Kornilova
 **/
public class TextResultView implements ResultView {
  private static final Logger LOG = Logger.getInstance(TextResultView.class);

  private final DataGrid myResultPanel;
  private final EditorEx myViewer;
  private final JComponent myComponent;
  private final TextRawIndexConverter myRawIndexConverter;
  private final ThreadPoolExecutor myExecutor = ConcurrencyUtil.newSingleThreadExecutor(getClass().getSimpleName());
  private Future<?> myLastExtractorTask = null;
  private boolean myTransposed;

  public TextResultView(@NotNull DataGrid resultPanel) {
    myResultPanel = resultPanel;
    myViewer = createEditor(myResultPanel.getProject(), "textView");
    myViewer.getScrollPane().setBorder(JBUI.Borders.empty());
    myComponent = UiDataProvider.wrapComponent(myViewer.getComponent(), sink -> {
      sink.set(CommonDataKeys.EDITOR, myViewer);
    });
    myViewer.setColorsScheme(myViewer.createBoundColorSchemeDelegate(myResultPanel.getEditorColorsScheme()));
    updateColorScheme(myViewer);
    ApplicationManager.getApplication().getMessageBus()
      .connect(this)
      .subscribe(EditorColorsManager.TOPIC, scheme -> {
        myResultPanel.getPanel().globalSchemeChange(scheme);
        reinitSettings();
      });
    myRawIndexConverter = new TextRawIndexConverter();
    GridModel<GridRow, GridColumn> model = myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    updateText(model.getRows());
  }

  @Override
  public void setValueAt(@Nullable Object v,
                         @NotNull ModelIndex<GridRow> row,
                         @NotNull ModelIndex<GridColumn> column,
                         boolean allowImmediateUpdate,
                         @NotNull GridRequestSource source) {

  }

  private static void updateColorScheme(@NotNull EditorEx editor) {
    Color backgroundColor = editor.getColorsScheme().getDefaultBackground();
    editor.setBackgroundColor(backgroundColor); // update scroll pane bg
    editor.getColorsScheme().setColor(EditorColors.GUTTER_BACKGROUND, backgroundColor);
  }

  @Override
  public void defaultBackgroundChanged() {
    updateColorScheme(myViewer);
  }

  public static @NotNull EditorEx createEditor(@NotNull Project project, @NotNull String name) {
    LightVirtualFile virtualFile = new LightVirtualFile(name, PlainTextLanguage.INSTANCE, "");
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    document = document != null ? document : EditorFactory.getInstance().createDocument("");
    EditorEx editor = (EditorEx)EditorFactory.getInstance().createViewer(document, project);

    editor.setEmbeddedIntoDialogWrapper(true);
    editor.getSettings().setFoldingOutlineShown(true);
    editor.getSettings().setCaretRowShown(true);
    editor.setCaretEnabled(true);
    editor.setHorizontalScrollbarVisible(true);
    editor.setVerticalScrollbarVisible(true);

    return editor;
  }

  @Override
  public void setTransposed(boolean transposed) {
    if (myTransposed == transposed) return;
    myTransposed = transposed;
    dataUpdated();
  }

  @Override
  public boolean isTransposed() {
    return myTransposed;
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return myViewer.getContentComponent();
  }

  @Override
  public void registerEscapeAction(@NotNull AbstractAction action) {
    String actionId = "grid.escape";
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    myViewer.getContentComponent().getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, actionId);
    myViewer.getContentComponent().getActionMap().put(actionId, action);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myComponent;
  }

  public @NotNull EditorEx getViewer() {
    return myViewer;
  }

  @Override
  public void resetLayout() {

  }

  @Override
  public void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state) {

  }

  @Override
  public void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean state) {

  }

  @Override
  public void showFirstCell(int rowNumOnCurrentPage) {

  }

  @Override
  public void growSelection() {

  }

  @Override
  public void shrinkSelection() {

  }

  @Override
  public void addSelectionChangedListener(@NotNull Consumer<Boolean> listener) {

  }

  @Override
  public void restoreColumnsOrder(Map<Integer, ModelIndex<GridColumn>> expectedToModel) {

  }

  @Override
  public boolean isEditing() {
    return false;
  }

  @Override
  public boolean isMultiEditingAllowed() {
    return false;
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getVisibleRows() {
    return ModelIndexSet.forRows(myResultPanel);
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getVisibleColumns() {
    return ModelIndexSet.forColumns(myResultPanel);
  }

  @Override
  public int getViewColumnCount() {
    return 0;
  }

  @Override
  public int getViewRowCount() {
    return 0;
  }

  @Override
  public boolean stopEditing() {
    return false;
  }

  @Override
  public void cancelEditing() {

  }

  @Override
  public boolean isViewModified() {
    return false;
  }

  @Override
  public void contentLanguageUpdated(@NotNull ModelIndex<GridColumn> idx, @NotNull Language language) {
  }

  @Override
  public void displayTypeUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType) {
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getContextColumn() {
    return ModelIndex.forColumn(myResultPanel, -1);
  }

  @Override
  public void updateSortKeysFromColumnAttributes() {

  }

  @Override
  public void orderingAndVisibilityChanged() {

  }

  @Override
  public @NotNull RawIndexConverter getRawIndexConverter() {
    return myRawIndexConverter;
  }

  @Override
  public void addMouseListenerToComponents(@NotNull MouseListener listener) {
    myViewer.addEditorMouseListener(new EditorMouseListener() {
      @Override
      public void mousePressed(@NotNull EditorMouseEvent event) {
        listener.mousePressed(event.getMouseEvent());
      }

      @Override
      public void mouseClicked(@NotNull EditorMouseEvent event) {
        listener.mouseClicked(event.getMouseEvent());
      }

      @Override
      public void mouseReleased(@NotNull EditorMouseEvent event) {
        listener.mouseReleased(event.getMouseEvent());
      }

      @Override
      public void mouseEntered(@NotNull EditorMouseEvent event) {
        listener.mouseEntered(event.getMouseEvent());
      }

      @Override
      public void mouseExited(@NotNull EditorMouseEvent event) {
        listener.mouseExited(event.getMouseEvent());
      }
    });
  }

  @Override
  public boolean supportsCustomSearchSession() {
    return false;
  }

  @Override
  public @Nullable SearchSession createSearchSession(@Nullable FindModel findModel, @Nullable Component previousFilterComponent) {
    return null;
  }

  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columns) {

  }

  @Override
  public void columnAttributesUpdated() {

  }

  @Override
  public void reinitSettings() {
    myViewer.reinitSettings();
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {

  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    dataUpdated();
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    dataUpdated();
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    dataUpdated();
  }

  @Override
  public void extractorFactoryChanged() {
    dataUpdated();
  }

  private void dataUpdated() {
    GridModel<GridRow, GridColumn> model = myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    updateText(ContainerUtil.copyList(model.getRows()));
  }

  private void updateText(@NotNull List<GridRow> rows) {
    Condition<?> disposed = (__) -> Disposer.isDisposed(this);
    ApplicationManager.getApplication().invokeLater(() -> {
      if (disposed.value(null)) return;
      DataExtractorFactory extractorFactory = DataExtractorFactories.getExtractorFactory(myResultPanel, GridUtil::suggestPlugin);
      ExtractorConfig config = ExtractorsHelper.getInstance(myResultPanel).createExtractorConfig(myResultPanel, myResultPanel.getObjectFormatter());
      DataExtractor extractor = extractorFactory.createExtractor(config);
      if (extractor == null) {
        LOG.error("Cannot create data extractor. DataExtractorFactory: " + extractorFactory.getName());
        updateEditorText(myViewer, myResultPanel.getProject(), "", PlainTextLanguage.INSTANCE);
        return;
      }
      Language guessedLanguage = LanguageUtil.getFileTypeLanguage(FileTypeRegistry.getInstance().getFileTypeByExtension(extractor.getFileExtension()));
      Language language = ObjectUtils.notNull(guessedLanguage, PlainTextLanguage.INSTANCE);
      GridModel<GridRow, GridColumn> model = myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
      List<GridColumn> allColumns = model.getColumns();
      int[] cols = model.getColumnIndices().asArray();
      var out = new Out.Readable();
      JBIterable<GridRow> iterable = JBIterable.from(rows);

      if (myLastExtractorTask != null) myLastExtractorTask.cancel(false);
      PageSpec pageOfThisExtractor = getCurrentPage();
      myLastExtractorTask = myExecutor.submit(() -> {
        DataExtractor.Extraction e = extractor.startExtraction(out, allColumns, "", builder().setTransposed(myTransposed).build(), cols);
        int size = iterable.size();
        int chunkSize;
        int showedRowsCount = 0;
        for (int i = 0; i < size; i += chunkSize) {
          chunkSize = chunkSize(i, extractor instanceof TranspositionAwareExtractor);
          e.addData(iterable.skip(i).take(chunkSize).toList());
          showedRowsCount += chunkSize;
          if (out.sizeInBytes() > FileSizeLimit.getDefaultContentLoadLimit() / 2) break;
        }
        e.complete();
        String postfix = size > showedRowsCount ? "\nResult is too big. Only first " + showedRowsCount + " entries are shown" : "";
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!getCurrentPage().equals(pageOfThisExtractor)) return;
          updateEditorText(myViewer, myResultPanel.getProject(), out.getString() + postfix, language);
        }, disposed);
      });
    }, disposed);
  }

  public static void updateEditorText(@NotNull EditorEx editor, @NotNull Project project, @NotNull String text, @NotNull Language language) {
    DocumentEx document = editor.getDocument();

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(0, document.getTextLength(), StringUtil.convertLineSeparators(text));
      CaretModel caretModel = editor.getCaretModel();
      if (caretModel.getOffset() >= document.getTextLength()) {
        caretModel.moveToOffset(document.getTextLength());
      }
    }), null, null);
    LightVirtualFile virtualFile = ObjectUtils.tryCast(FileDocumentManager.getInstance().getFile(document), LightVirtualFile.class);
    if (virtualFile == null) return;

    if (virtualFile.getLanguage() == language) return;
    virtualFile.setLanguage(language);
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(project, virtualFile);
    editor.setHighlighter(highlighter);
    FileContentUtilCore.reparseFiles(virtualFile);
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, !language.getID().equals("SQL"));
  }

  private static int chunkSize(int startIdx, boolean isTranspositionAware) {
    if (isTranspositionAware) return 100;
    if (startIdx < 20) return 2;
    if (startIdx < 100) return 5;
    else return 20;
  }

  private @NotNull PageSpec getCurrentPage() {
    GridPagingModel<GridRow, GridColumn> pageModel = myResultPanel.getDataHookup().getPageModel();
    return new PageSpec(pageModel.getPageStart(), pageModel.getPageEnd());
  }

  @Override
  public void searchSessionUpdated() {

  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myViewer);
    myExecutor.shutdown();
  }

  @TestOnly
  public void shutdownAndAwaitTermination() {
    myExecutor.shutdown();
    try {
      myExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      LOG.error("Text result view termination interrupted", e);
    }
  }

  @Override
  public void resetScroll() {
    myViewer.getScrollPane().getHorizontalScrollBar().setValue(0);
    myViewer.getScrollPane().getVerticalScrollBar().setValue(0);
  }

  private static class PageSpec {
    final int myPageStart;
    final int myPageEnd;

    PageSpec(int pageStart, int pageEnd) {
      myPageStart = pageStart;
      myPageEnd = pageEnd;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PageSpec spec = (PageSpec)o;
      return myPageStart == spec.myPageStart &&
             myPageEnd == spec.myPageEnd;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myPageStart, myPageEnd);
    }

    @Override
    public String toString() {
      return "PageSpec{" + myPageStart + ", " + myPageEnd + '}';
    }
  }
}
