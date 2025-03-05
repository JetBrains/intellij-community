package com.intellij.database.run.ui.grid.renderers;

import com.intellij.codeInsight.daemon.impl.HintRenderer;
import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.editor.DataGridColors;
import com.intellij.database.extractors.ImageInfo;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ResultReference;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextFieldCellRenderer.AbbreviatingRendererComponent;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Map;
import java.util.Objects;

import static com.intellij.database.datagrid.GridUtil.createFormatterConfig;
import static com.intellij.database.datagrid.mutating.ColumnDescriptor.Attribute.HIGHLIGHTED;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;
import static com.intellij.database.run.ui.grid.renderers.DefaultTextRendererFactory.TextRenderer.hasInlay;

public final class DefaultTextRendererFactory implements GridCellRendererFactory {

  private final DataGrid myGrid;
  private final Map<String, TextRenderer> myRenderers; // language id to renderer
  private final Map<String, TextRenderer> myRenderersWithInlay; // language id to renderer

  public DefaultTextRendererFactory(@NotNull DataGrid grid) {
    myGrid = grid;
    myRenderers = factory(false);
    myRenderersWithInlay = factory(true);
  }

  private @NotNull Map<String, TextRenderer> factory(boolean withInlay) {
    return FactoryMap.createMap(languageId -> {
      TextRenderer renderer = new TextRenderer(myGrid, languageId, withInlay);
      Disposer.register(myGrid, renderer);
      return renderer;
    }, () -> new Reference2ObjectOpenHashMap<>());
  }

  @Override
  public boolean supports(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return true;
  }

  @Override
  public @NotNull GridCellRenderer getOrCreateRenderer(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    Object value = myGrid.getDataModel(DATA_WITH_MUTATIONS).getValueAt(row, column);
    String languageId = getLanguage(myGrid, row, column).getID();
    return hasInlay(value) ? myRenderersWithInlay.get(languageId) : myRenderers.get(languageId);
  }

  @Override
  public void reinitSettings() {
    myRenderers.forEach((lang, renderer) -> renderer.reinitSettings());
    myRenderersWithInlay.forEach((lang, renderer) -> renderer.reinitSettings());
  }

  public static @NotNull Language getLanguage(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> columnIdx) {
    Language language = grid.getContentLanguage(columnIdx);
    if (language != Language.ANY) return language;
    language = GridHelper.get(grid).getCellLanguage(grid, row, columnIdx);
    return language == null ? PlainTextLanguage.INSTANCE : language;
  }

  private static @Nullable GridColumn getColumn(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull CoreGrid<GridRow, GridColumn> grid) {
    return grid.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
  }

  public static class TextRenderer extends GridCellRenderer {
    private static final TextAttributes BOLD_TEXT_ATTRIBUTES = new TextAttributes();
    private final Map<Pair<ModelIndex<GridColumn>, Object>, String> myValueTextCache =
      CollectionFactory.createConcurrentWeakKeySoftValueMap(10, 0.75f, 1, new HashingStrategy<>() {
        @Override
        public int hashCode(Pair<ModelIndex<GridColumn>, Object> object) {
          return Objects.hash(object.getFirst(), System.identityHashCode(object.getSecond()));
        }

        @Override
        public boolean equals(Pair<ModelIndex<GridColumn>, Object> o1, Pair<ModelIndex<GridColumn>, Object> o2) {
          return o1.getFirst().equals(o2.getFirst()) && o1.getSecond() == o2.getSecond();
        }
      });

    private final Border myEmptyBorder;
    private final String myLanguageId;
    private final boolean myWithInlay;
    private AbbreviatingRendererComponent myComponent;
    private Inlay<HintRenderer> inlay;
    private final HintRenderer myInlayRenderer = new HintRenderer("") {
      @Override
      protected boolean useEditorFont() {
        return true;
      }
    };

    static {
      BOLD_TEXT_ATTRIBUTES.setFontType(Font.BOLD);
    }

    public TextRenderer(@NotNull DataGrid grid) {
      this(grid, defaultEmptyBorder());
    }

    public TextRenderer(@NotNull DataGrid grid, @NotNull Border emptyBorder) {
      this(grid, PlainTextLanguage.INSTANCE.getID(), false, emptyBorder);
    }

    public TextRenderer(@NotNull DataGrid grid, @NotNull String languageId, boolean withInlay) {
      this(grid, languageId, withInlay, defaultEmptyBorder());
    }

    public TextRenderer(@NotNull DataGrid grid, @NotNull String languageId, boolean withInlay, @NotNull Border emptyBorder) {
      super(grid);
      myLanguageId = languageId;
      myWithInlay = withInlay;
      myEmptyBorder = emptyBorder;

      UiNotifyConnector.installOn(grid.getPanel().getComponent(), new Activatable() {
        @Override
        public void hideNotify() {
          myValueTextCache.clear();
        }
      });
    }

    private static Border defaultEmptyBorder() {
      return JBUI.Borders.empty(2, 3);
    }

    @Override
    public void clearCache() {
      myValueTextCache.clear();
    }

    @Override
    public void reinitSettings() {
      if (myComponent != null) {
        myComponent.getEditor().reinitSettings();
      }
    }

    public static @NotNull AbbreviatingRendererComponent createComponent(@NotNull Project project, @Nullable Language language) {
      return language == PlainTextLanguage.INSTANCE ?
             new AbbreviatingRendererComponent(project, language, false, true) :
             new AbbreviatingRendererComponent(project, language, false, false, false, ' ');
    }

    @Override
    public int getSuitability(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      return SUITABILITY_MIN;
    }

    @Override
    public @NotNull JComponent getComponent(@NotNull ViewIndex<GridRow> rowIdx, @NotNull ViewIndex<GridColumn> columnIdx, @Nullable Object value) {
      Pair<Integer, Integer> modelRowAndColumn =
        myGrid.getRawIndexConverter().rowAndColumn2Model().fun(rowIdx.asInteger(), columnIdx.asInteger());
      ModelIndex<GridColumn> modelColumn = ModelIndex.forColumn(myGrid, modelRowAndColumn.second);
      return getComponent(rowIdx, columnIdx, value, modelColumn);
    }

    public @NotNull JComponent getComponent(@NotNull ViewIndex<GridRow> rowIdx,
                                            @NotNull ViewIndex<GridColumn> columnIdx,
                                            @Nullable Object value,
                                            @NotNull ModelIndex<GridColumn> modelColumn) {
      if (myComponent == null) {
        myComponent = createComponent(myGrid.getProject(), Language.findLanguageByID(myLanguageId));
        if (myWithInlay) {
          inlay = myComponent.getEditor().getInlayModel().addInlineElement(0, myInlayRenderer);
        }
      }
      EditorEx editor = myComponent.getEditor();
      configureEditor(editor);
      boolean selected = myGrid.getSelectionModel().isSelected(rowIdx, columnIdx);
      GridColumn column = getColumn(modelColumn, myGrid);
      TextAttributes attributes = getAttributes(value, editor.getColorsScheme(), selected,
                                                column != null && column.getAttributes().contains(HIGHLIGHTED));

      ModelIndex<GridRow> modelRow = rowIdx.toModel(myGrid);

      String valueText = getValueText(modelColumn, modelRow, value == null ? ReservedCellValue.NULL : value);

      myComponent.setText(valueText, attributes, selected);
      myComponent.setBorder(myEmptyBorder);

      if (myWithInlay) {
        String inlayMessage = StringUtil.notNullize(getInlayMessage(value, modelColumn, myGrid));
        myInlayRenderer.setText(inlayMessage.isEmpty() ? " " : inlayMessage);
        inlay.update();
      }
      return myComponent;
    }

    static boolean hasInlay(@Nullable Object value) {
      return value instanceof LobInfo && ((LobInfo<?>)value).isTruncated();
    }

    private static @Nullable String getInlayMessage(@Nullable Object value,
                                                    @NotNull ModelIndex<GridColumn> modelColumn,
                                                    DataGrid grid) {
      if (!hasInlay(value)) return null;
      LobInfo<?> info = (LobInfo<?>)value;
      if (info instanceof LobInfo.ClobInfo && ((LobInfo.ClobInfo)info).data == null ||
          info instanceof LobInfo.BlobInfo && ((LobInfo.BlobInfo)info).data == null) {
        GridColumn column = getColumn(modelColumn, grid);
        return (column == null ? "" : "(" + StringUtil.toUpperCase(column.getTypeName()) + ") ") +
               StringUtil.formatFileSize(info.length, "");
      }
      return DataGridBundle.message("Console.TableResult.n.bytes.of.m.bytes.loaded",
                                    StringUtil.formatFileSize(info.getLoadedDataLength(), ""),
                                    StringUtil.formatFileSize(info.length, ""));
    }

    public static TextAttributes getAttributes(@Nullable Object value,
                                               @NotNull TextAttributesScheme scheme,
                                               boolean selected,
                                               boolean highlight) {
      if (highlight) return BOLD_TEXT_ATTRIBUTES;
      TextAttributesKey attributesKey = getAttributesKey(value);
      return !selected && attributesKey != null ? scheme.getAttributes(attributesKey) : null;
    }

    protected void configureEditor(@NotNull EditorEx editor) {
      configureEditor(editor, myGrid);
    }

    public static void configureEditor(@NotNull EditorEx editor, @NotNull DataGrid grid) {
      EditorColorsScheme scheme = editor.getColorsScheme();
      boolean schemeAlreadySet = scheme instanceof DelegateColorScheme &&
                                 ((DelegateColorScheme)scheme).getDelegate() == grid.getColorsScheme() &&
                                 scheme.getEditorFontSize() == grid.getPreferredFocusedComponent().getFont().getSize();
      if (schemeAlreadySet) return;
      EditorColorsScheme bounded = editor.createBoundColorSchemeDelegate(grid.getColorsScheme());
      bounded.setEditorFontSize(grid.getPreferredFocusedComponent().getFont().getSize());
      editor.setColorsScheme(bounded);
    }

    protected @NotNull String getValueText(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Object value) {
      var key = Pair.pair(columnIdx, value);
      String cachedText = value instanceof ReservedCellValue ? null : myValueTextCache.get(key);
      if (cachedText != null) {
        return cachedText;
      }
      String text = getDisplayString(getText(value, columnIdx, myGrid));
      if (!(value instanceof ReservedCellValue)) {
        myValueTextCache.put(key, text);
      }
      return text;
    }

    protected @NotNull String getValueText(@NotNull ModelIndex<GridColumn> columnIdx,
                                           @Nullable ModelIndex<GridRow> rowIdx,
                                           @NotNull Object value) {
      return getValueText(columnIdx, value);
    }

    private static @NotNull String getText(@NotNull Object value, @NotNull ModelIndex<GridColumn> columnIdx, @NotNull DataGrid grid) {
      GridColumn column = getColumn(columnIdx, grid);
      if (column == null) return value.toString();
      String stringValue = grid.getObjectFormatter().objectToString(value, column, createFormatterConfig(grid, columnIdx));
      return Objects.requireNonNullElse(stringValue, "null");
    }

    public static String getDisplayString(@NotNull String str) {
      if (GridUtil.isFailedToLoad(str)) {
        int lineEnd = str.indexOf('\n', GridUtilCore.FAILED_TO_LOAD_PREFIX.length() + 1);
        if (lineEnd > -1) {
          str = str.substring(GridUtilCore.FAILED_TO_LOAD_PREFIX.length(), lineEnd).trim();
        }
      }
      return trimTrailingWhitespace(str);
    }

    private static String trimTrailingWhitespace(String s) {
      int end = s.length();
      while (end > 0 && s.charAt(end - 1) <= ' ' && s.charAt(end - 1) != '\n') end--;
      return s.substring(0, end);
    }

    @Override
    public void dispose() {
      if (myComponent != null) Disposer.dispose(myComponent);
    }


    public static @Nullable TextAttributesKey getAttributesKey(@Nullable Object value) {
      if (value == null || value == ReservedCellValue.UNSET) {
        return DataGridColors.GRID_NULL_VALUE;
      }
      else if (value instanceof ImageInfo) {
        return DataGridColors.GRID_IMAGE_VALUE;
      }
      else if (GridUtil.isFailedToLoad(value)) {
        return DataGridColors.GRID_ERROR_VALUE;
      }
      else if (value instanceof LobInfo.FileClobInfo || value instanceof LobInfo.FileBlobInfo) {
        return DataGridColors.GRID_UPLOAD_VALUE;
      }
      else if (value instanceof ResultReference) {
        return DataGridColors.GRID_RESULT_VALUE;
      }
      return null;
    }
  }
}
