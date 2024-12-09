// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.TerminalUtils;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.IntStream;

/**
 * @author gregsh
 */
final class ScratchImplUtil {
  private ScratchImplUtil() {
  }

  public static void updateFileExtension(@NotNull Project project,
                                         @Nullable VirtualFile file,
                                         @NotNull LanguageItem item) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (CommandProcessor.getInstance().getCurrentCommand() == null) {
      throw new AssertionError("command required");
    }

    if (file == null) return;
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType prevType = getFileTypeFromName(file.getName(), fileTypeManager);
    Language currLanguage = LanguageUtil.getLanguageForPsi(project, file, item.fileType);
    FileType currType = currLanguage == null ? item.fileType : ObjectUtils.notNull(currLanguage.getAssociatedFileType(), item.fileType);
    if (prevType == currType) return;

    String nameWithoutExtension = getNameWithoutExtension(file, fileTypeManager);

    VirtualFile parent = file.getParent();
    String newName = parent != null ? VfsUtil.getNextAvailableName(parent, nameWithoutExtension, item.fileExtension) :
                     PathUtil.makeFileName(nameWithoutExtension, item.fileExtension);
    file.rename(ScratchImplUtil.class, newName);
  }

  private static @NotNull String getNameWithoutExtension(@NotNull VirtualFile file,
                                                         @NotNull FileTypeManager fileTypeManager) {
    return getNameWithoutExtension(file.getName(), file.isCaseSensitive(), fileTypeManager);
  }

  private static @NotNull String getNameWithoutExtension(@NotNull String fileName,
                                                         boolean caseSensitive,
                                                         @NotNull FileTypeManager fileTypeManager) {
    // support multipart extensions like *.blade.php
    String extension = StringUtil.notNullize(PathUtilRt.getFileExtension(fileName));
    if (extension.isEmpty()) return fileName;
    FileType fileType = fileTypeManager.getFileTypeByFileName(fileName);
    if (fileType != FileTypes.UNKNOWN) {
      extension = getFileTypeExtensions(fileType, false, fileTypeManager)
        .reduce(extension, (val, o) -> val.length() < o.length() && hasExtension(fileName, o, caseSensitive) ? o : val);
    }
    return fileName.substring(0, fileName.length() - extension.length() - 1);
  }

  public static boolean hasMatchingExtension(@NotNull Project project, @NotNull VirtualFile file) {
    String fileName = file.getName();
    if (PathUtilRt.getFileExtension(fileName) == null) return false;
    boolean caseSensitive = file.isCaseSensitive();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType expected = getFileTypeFromName(fileName, fileTypeManager);
    Language language = LanguageUtil.getLanguageForPsi(project, file);
    FileType actual = language == null ? null : language.getAssociatedFileType();
    if (actual == null) return expected != null;
    if (expected == actual) return true;
    return getFileTypeExtensions(actual, false, fileTypeManager)
      .filter(o -> hasExtension(fileName, o, caseSensitive))
      .isNotEmpty();
  }

  private static boolean hasExtension(@NotNull String fileName, @NotNull String extension, boolean caseSensitive) {
    if (fileName.length() < extension.length() + 1) return false;
    if (fileName.charAt(fileName.length() - extension.length() - 1) != '.') return false;
    return StringUtilRt.equal(fileName.subSequence(fileName.length() - extension.length(), fileName.length()), extension, caseSensitive);
  }

  static @Nullable FileType getFileTypeFromName(@NotNull String fileName, @NotNull FileTypeManager fileTypeManager) {
    if (PathUtilRt.getFileExtension(fileName) == null) return null;
    FileType result = fileTypeManager.getFileTypeByFileName(fileName);
    if (result == UnknownFileType.INSTANCE || getFileTypeExtensions(result, false, fileTypeManager).isEmpty()) {
      return null;
    }
    return result;
  }

  static @NotNull JBIterable<String> getFileTypeExtensions(@NotNull FileType fileType,
                                                           boolean sort,
                                                           @NotNull FileTypeManager fileTypeManager) {
    JBIterable<String> result = JBIterable.from(fileTypeManager.getAssociations(fileType))
      .filterMap(o -> {
        if (o instanceof ExtensionFileNameMatcher) {
          return StringUtil.nullize(((ExtensionFileNameMatcher)o).getExtension());
        }
        else if (o instanceof WildcardFileNameMatcher) {
          String pattern = ((WildcardFileNameMatcher)o).getPattern();
          if (pattern.startsWith("*.") && pattern.indexOf('*', 1) < 0 && pattern.indexOf('?', 1) < 0) {
            return StringUtil.nullize(pattern.substring(2));
          }
        }
        return null;
      });
    if (sort) {
      String def = fileType.getDefaultExtension();
      return result.sort((o1, o2) -> o1.equals(o2) ? 0 : o1.equals(def) ? -1 : o2.equals(def) ? 1 : Integer.compare(o1.length(), o2.length()));
    }
    return result;
  }

  static @Nullable VirtualFile findFileIgnoreExtension(@NotNull VirtualFile dir, @NotNull String fileNameAndExt) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    String fileName = getNameWithoutExtension(fileNameAndExt, dir.isCaseSensitive(), fileTypeManager);
    for (VirtualFile child : dir.getChildren()) {
      if (child.isDirectory()) continue;
      String childName = getNameWithoutExtension(child, fileTypeManager);
      if (!StringUtil.equalsIgnoreCase(childName, fileName)) continue;
      return child;
    }
    return null;
  }

  static @NotNull String getNextAvailableName(@NotNull VirtualFile dir, @NotNull String fileNameAndExt) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    IntSet existing = new IntOpenHashSet();
    String fileName = getNameWithoutExtension(fileNameAndExt, dir.isCaseSensitive(), fileTypeManager);
    int fileNameLen = fileName.length();
    String fileExt = fileNameAndExt.length() > fileNameLen + 1 ? fileNameAndExt.substring(fileNameLen + 1) : "";
    for (VirtualFile child : dir.getChildren()) {
      if (child.isDirectory()) continue;
      String childName = getNameWithoutExtension(child, fileTypeManager);
      if (!StringUtil.startsWithIgnoreCase(childName, fileName)) continue;
      if (childName.length() == fileNameLen) {
        existing.add(0);
      }
      else if (childName.length() > fileNameLen + 1 &&
               childName.charAt(fileNameLen) == '_') {
        int val = StringUtil.parseInt(childName.substring(fileNameLen + 1), -1);
        if (val > -1) existing.add(val);
      }
    }
    int num = 0;
    while (existing.contains(num)) num++;

    return PathUtil.makeFileName(num == 0 ? fileName : fileName + "_" + num, fileExt);
  }

  static void changeLanguageWithUndo(@NotNull Project project,
                                     @NotNull LanguageItem item,
                                     VirtualFile @NotNull [] sortedFiles,
                                     @NotNull PerFileMappings<Language> mappings) throws UnexpectedUndoException {
    ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(Arrays.asList(sortedFiles));
    if (status.hasReadonlyFiles()) return;

    final Set<VirtualFile> matchedExtensions = new LinkedHashSet<>();
    final Map<VirtualFile, Language> oldMapping = new HashMap<>();
    for (VirtualFile file : sortedFiles) {
      oldMapping.put(file, mappings.getMapping(file));
      if (hasMatchingExtension(project, file)) {
        matchedExtensions.add(file);
      }
    }

    BasicUndoableAction action = new BasicUndoableAction(sortedFiles) {
      @Override
      public void undo() {
        for (VirtualFile file : sortedFiles) {
          mappings.setMapping(file, oldMapping.get(file));
        }
      }

      @Override
      public void redo() {
        for (VirtualFile file : sortedFiles) {
          mappings.setMapping(file, item.language);
        }
      }
    };
    action.redo();
    UndoManager.getInstance(project).undoableActionPerformed(action);

    for (VirtualFile file : matchedExtensions) {
      try {
        updateFileExtension(project, file, item);
      }
      catch (IOException ignored) {
      }
    }
  }

  static LRUPopupBuilder<LanguageItem> buildLanguagesPopup(@NotNull Project project,
                                                           @NotNull @NlsContexts.PopupTitle String title) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    List<LanguageItem> items = JBIterable.from(LanguageUtil.getFileLanguages())
      .map(LanguageItem::fromLanguage)
      .append(JBIterable.of(fileTypeManager.getRegisteredFileTypes())
                .filter(AbstractFileType.class)
                .map(o -> LanguageItem.fromAbstractFileType(o, getFileTypeExtensions(o, true, fileTypeManager).first())))
      .filter(Objects::nonNull)
      .toList();

    Comparator<LanguageItem> comparator = (o1, o2) -> StringUtil.naturalCompare(o1.getDisplayName(), o2.getDisplayName());
    return new LRUPopupBuilder<LanguageItem>(project, title) {
      @Override
      public String getDisplayName(LanguageItem o) {
        return o.getDisplayName();
      }

      @Override
      public Icon getIcon(LanguageItem o) {
        return ObjectUtils.notNull(o.fileType.getIcon(), AllIcons.FileTypes.Any_type);
      }

      @Override
      public String getStorageId(LanguageItem o) {
        return o.language != null ? o.language.getID() : o.fileType.getName();
      }
    }
      .forValues(items)
      .withFilter(it -> it.language == null || ScratchFileCreationHelper.EXTENSION.forLanguage(it.language).newScratchAllowed())
      .withComparator(comparator)
      .withExtraSpeedSearchNamer(o -> o.fileExtension)
      .withSelection(null);
  }

  record LanguageItem(@Nullable Language language, @NotNull FileType fileType, @NotNull String fileExtension) {
    @NotNull @NlsSafe String getDisplayName() {
      return language != null ? language.getDisplayName() : fileType.getDescription() + " (*." + fileExtension + ")";
    }

    static @Nullable LanguageItem fromLanguage(@NotNull Language language) {
      LanguageFileType fileType = language.getAssociatedFileType();
      String defaultExtension = fileType == null ? null : fileType.getDefaultExtension();
      if (StringUtil.isEmpty(defaultExtension)) return null;
      return new LanguageItem(language, fileType, defaultExtension);
    }

    static @Nullable LanguageItem fromAbstractFileType(@NotNull AbstractFileType fileType, @Nullable String fileExtension) {
      if (fileExtension == null) return null;
      return new LanguageItem(null, fileType, fileExtension);
    }
  }

  static @Nullable TextExtractor getTextExtractor(@Nullable Component component) {
    return component instanceof JTextComponent ? new TextComponentExtractor((JTextComponent)component) :
           component instanceof JList ? new ListExtractor((JList<?>)component) :
           component instanceof JTree ? new TreeExtractor((JTree)component) :
           component instanceof JTable ? new TableExtractor((JTable)component) :
           TerminalUtils.isTerminalComponent(component) ? new TerminalExtractor(component) :
           null;
  }

  interface TextExtractor {
    boolean hasSelection();
    @Nullable String extractText();
  }

  private static final class TerminalExtractor implements TextExtractor {
    final Component component;

    TerminalExtractor(@Nullable Component dataContext) {
      component = dataContext;
    }

    @Override
    public boolean hasSelection() {
      return TerminalUtils.hasSelectionInTerminal(component);
    }

    @Override
    public String extractText() {
      if (TerminalUtils.hasSelectionInTerminal(component)) {
        return TerminalUtils.getSelectedTextInTerminal(component);
      }
      String text = TerminalUtils.getTextInTerminal(component);
      return StringUtil.isEmptyOrSpaces(text) ? null : text;
    }
  }

  private static final class TextComponentExtractor implements TextExtractor {
    final JTextComponent comp;

    TextComponentExtractor(@NotNull JTextComponent component) { comp = component; }

    @Override
    public boolean hasSelection() {
      return comp.getSelectionStart() != comp.getSelectionEnd();
    }

    @Override
    public String extractText() {
      String text = comp.getSelectedText();
      return StringUtil.isEmpty(text) ? comp.getText() : text;
    }
  }

  private static final class ListExtractor implements TextExtractor {
    final JList<Object> comp;

    /** @noinspection unchecked*/
    ListExtractor(@NotNull JList<?> component) { comp = (JList<Object>)component; }

    @Override
    public boolean hasSelection() {
      return comp.getSelectionModel().getSelectedItemsCount() > 1;
    }

    @Override
    public String extractText() {
      List<Object> selection = comp.getSelectedValuesList();
      JBIterable<Object> values;
      if (selection.size() > 1) {
        values = JBIterable.from(selection);
      }
      else {
        ListModel<Object> model = comp.getModel();
        values = JBIterable.generate(0, o -> o + 1).take(model.getSize()).map(o -> model.getElementAt(o));
      }
      ListCellRenderer<Object> renderer = comp.getCellRenderer();
      StringBuilder sb = new StringBuilder();
      for (Object value : values) {
        append(sb, value, renderer.getListCellRendererComponent(comp, value, -1, false, false));
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  private static final class TreeExtractor implements TextExtractor {
    final JTree comp;

    TreeExtractor(@NotNull JTree component) { comp = component; }

    @Override
    public boolean hasSelection() {
      return comp.getSelectionModel().getSelectionCount() > 1;
    }

    @Override
    public String extractText() {
      TreePath[] selection = comp.getSelectionPaths();
      int initialDepth;
      JBIterable<TreePath> paths;
      JBTreeTraverser<TreePath> traverser = TreeUtil.treePathTraverser(comp).expand(comp::isExpanded);
      if (selection != null && selection.length > 1) {
        paths = traverser.withRoots(selection).unique().traverse();
        initialDepth = Integer.MAX_VALUE;
        for (TreePath path : selection) {
          initialDepth = Math.min(initialDepth, path.getPathCount() - 1);
        }
      }
      else {
        initialDepth = comp.isRootVisible() ? 0 : 1;
        paths = traverser.traverse().skip(initialDepth);
      }
      TreeCellRenderer renderer = comp.getCellRenderer();
      StringBuilder sb = new StringBuilder();
      for (TreePath path : paths) {
        Object value = path.getLastPathComponent();
        int depth = path.getPathCount() - initialDepth - 1;
        //noinspection StringRepeatCanBeUsed
        for (int i = 0; i < depth; i++) sb.append("  ");
        append(sb, value, renderer.getTreeCellRendererComponent(comp, value, false, false, false, -1, false));
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  private static final class TableExtractor implements TextExtractor {
    final JTable comp;

    TableExtractor(@NotNull JTable component) { comp = component; }

    @Override
    public boolean hasSelection() {
      return comp.getSelectionModel().getSelectedItemsCount() > 1;
    }

    @Override
    public String extractText() {
      int[] rows = comp.getSelectedRows();
      int[] cols = comp.getColumnSelectionAllowed() ?
                   comp.getSelectedColumns() : IntStream.range(0, comp.getColumnCount()).toArray();
      StringBuilder sb = new StringBuilder();
      int lastCol = cols.length == 0 ? -1 : cols[cols.length - 1];
      for (int row : rows) {
        for (int col : cols) {
          Object value = comp.getModel().getValueAt(comp.convertRowIndexToModel(row), comp.convertColumnIndexToModel(col));
          TableCellRenderer renderer = comp.getCellRenderer(row, col);
          append(sb, value, renderer.getTableCellRendererComponent(comp, value, false, false, row, col));
          if (col != lastCol) sb.append("    ");
        }
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  private static void append(StringBuilder sb, Object value, Component renderer) {
    int length = sb.length();
    if (renderer instanceof JPanel) {
      for (Component c : UIUtil.uiTraverser(renderer)) {
        if (appendSimple(sb, c)) sb.append(" ");
      }
      if (sb.length() == length) {
        sb.append(TreeUtil.getUserObject(value));
      }
    }
    else if (!appendSimple(sb, renderer)) {
      sb.append(TreeUtil.getUserObject(value));
    }
    // replace various space chars like `FontUtil#thinSpace` with just space
    for (int i = length, len = sb.length(); i < len; i++) {
      char c = sb.charAt(i);
      if (c >= '\u2000' && c <= '\u2009') sb.setCharAt(i, ' ');
    }
  }

  private static boolean appendSimple(@NotNull StringBuilder sb, @Nullable Component renderer) {
    if (renderer instanceof JLabel) {
      sb.append(((JLabel)renderer).getText());
    }
    else if (renderer instanceof JTextComponent) {
      sb.append(((JTextComponent)renderer).getText());
    }
    else if (renderer instanceof SimpleColoredComponent) {
      sb.append(((SimpleColoredComponent)renderer).getCharSequence(false));
    }
    else {
      return false;
    }
    return true;
  }
}
