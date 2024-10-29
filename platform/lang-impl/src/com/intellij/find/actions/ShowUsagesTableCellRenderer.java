// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.find.FindBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.*;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.usages.*;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.util.ui.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleTable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;


@ApiStatus.Internal
final class ShowUsagesTableCellRenderer implements TableCellRenderer {

  static final int MARGIN = 2;

  private final @NotNull Predicate<? super Usage> myOriginUsageCheck;
  private final @NotNull AtomicInteger myOutOfScopeUsages;
  private final @NotNull SearchScope mySearchScope;

  ShowUsagesTableCellRenderer(
    @NotNull Predicate<? super Usage> originUsageCheck,
    @NotNull AtomicInteger outOfScopeUsages,
    @NotNull SearchScope searchScope
  ) {
    myOriginUsageCheck = originUsageCheck;
    myOutOfScopeUsages = outOfScopeUsages;
    mySearchScope = searchScope;
  }

  private static final int CURRENT_ASTERISK_COL = 0;
  private static final int FILE_GROUP_COL = 1;
  private static final int LINE_NUMBER_COL = 2;
  private static final int USAGE_TEXT_COL = 3;
  @MagicConstant(intValues = {CURRENT_ASTERISK_COL, FILE_GROUP_COL, LINE_NUMBER_COL, USAGE_TEXT_COL})
  private @interface UsageTableColumn {
  }

  @DirtyUI
  @Override
  public Component getTableCellRendererComponent(
    JTable list,
    Object value,
    boolean isSelected,
    boolean hasFocus,
    int row,
    @UsageTableColumn int column
  ) {
    UsageNode usageNode = (UsageNode)value;
    Usage usage = usageNode == null ? null : usageNode.getUsage();

    Color selectionBg = UIUtil.getListSelectionBackground(true);
    Color selectionFg = NamedColorUtil.getListSelectionForeground(true);
    Color rowSelectionBackground = isSelected ? selectionBg : null;
    Color rowForeground = isSelected ? selectionFg : list.getForeground();

    if (usageNode == null || usageNode instanceof ShowUsagesAction.StringNode) {
      SimpleColoredComponent textChunks = new SimpleColoredComponent();
      if (usageNode == null) {
        textChunks.append("", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      else {
        textChunks.append(((ShowUsagesAction.StringNode)value).getString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      return textComponentSpanningWholeRow(textChunks, list.getBackground(), rowSelectionBackground, rowForeground, column, list);
    }
    if (usage == ((ShowUsagesTable)list).MORE_USAGES_SEPARATOR) {
      SimpleColoredComponent textChunks = new SimpleColoredComponent();
      textChunks.append("...<");
      textChunks.append(FindBundle.message("show.usages.more.usages.label"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      textChunks.append(">...");
      return textComponentSpanningWholeRow(textChunks, list.getBackground(), rowSelectionBackground, rowForeground, column, list);
    }
    if (usage == ((ShowUsagesTable)list).USAGES_OUTSIDE_SCOPE_SEPARATOR) {
      String message = UsageViewManagerImpl.outOfScopeMessage(myOutOfScopeUsages.get(), mySearchScope);
      SimpleColoredComponent textChunks = new SimpleColoredComponent();
      textChunks.append("...<");
      textChunks.append(message, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      textChunks.append(">...");
      return textComponentSpanningWholeRow(textChunks, list.getBackground(), rowSelectionBackground, rowForeground, column, list);
    }
    if (usage == ((ShowUsagesTable)list).USAGES_FILTERED_OUT_SEPARATOR) {
      ShowUsagesAction.FilteredOutUsagesNode filtered = (ShowUsagesAction.FilteredOutUsagesNode)usageNode;
      SimpleColoredComponent textChunks = new SimpleColoredComponent();
      textChunks.append(filtered.getString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      JComponent component = textComponentSpanningWholeRow(textChunks, list.getBackground(), rowSelectionBackground, rowForeground, column, list);
      component.setToolTipText(filtered.getTooltip());
      return component;
    }

    UsagePresentation presentation = usage.getPresentation();
    UsageNodePresentation cachedPresentation = presentation.getCachedPresentation();
    Color fileBgColor = cachedPresentation == null ? presentation.getBackgroundColor() : cachedPresentation.getBackgroundColor();
    Color rowBackground =  fileBgColor == null ? list.getBackground() : fileBgColor;

    // greying the current usage the "find usages" was originated from
    boolean isOriginUsage = myOriginUsageCheck.test(usage);

    SelectablePanel panel = new SelectablePanel() {
      @Override
      public @NotNull AccessibleContext getAccessibleContext() {
        AccessibleContext acc = super.getAccessibleContext();
        if (column == CURRENT_ASTERISK_COL) {
          acc.setAccessibleName(getAccessibleNameForRow(list, row, isOriginUsage));
        }
        return acc;
      }
    };

    LayoutManager layout = switch (column) {
      case USAGE_TEXT_COL -> new BorderLayout();
      default -> new MyLayout(panel);
    };

    panel.setLayout(layout);
    panel.setFont(null);

    if (isOriginUsage && !ExperimentalUI.isNewUI()) {
      rowBackground = slightlyDifferentColor(rowBackground);
      rowSelectionBackground = slightlyDifferentColor(rowSelectionBackground);
      fileBgColor = slightlyDifferentColor(fileBgColor);
      selectionBg = slightlyDifferentColor(selectionBg);
    }
    panel.setForeground(rowForeground);
    applyBackground(panel, column, rowBackground, rowSelectionBackground);

    TextChunk[] text = cachedPresentation == null ? presentation.getText() : cachedPresentation.getText();

    switch (column) {
      case CURRENT_ASTERISK_COL -> {
        if (!ExperimentalUI.isNewUI() && isOriginUsage) {
          panel.add(new JLabel(isSelected ? AllIcons.General.ModifiedSelected : AllIcons.General.Modified));
        }
      }
      case FILE_GROUP_COL -> {
        appendGroupText(list, (GroupNode)usageNode.getParent(), panel, fileBgColor, isSelected);
      }
      case LINE_NUMBER_COL -> {
        SimpleColoredComponent textChunks = new SimpleColoredComponent();
        textChunks.setOpaque(false);
        if (text.length != 0) {
          TextChunk chunk = text[0];
          textChunks.append(chunk.getText(), getAttributes(isSelected, fileBgColor, selectionBg, selectionFg, chunk));
        }
        SpeedSearchUtil.applySpeedSearchHighlighting(list, textChunks, false, isSelected);

        panel.add(textChunks);
        panel.getAccessibleContext().setAccessibleName(IdeBundle.message("ShowUsagesTableCellRenderer.accessible.LINE_NUMBER_COL",
                                                                         textChunks.getAccessibleContext().getAccessibleName()));
      }
      case USAGE_TEXT_COL -> {
        SimpleColoredComponent textChunks = new SimpleColoredComponent();
        textChunks.setOpaque(false);

        Icon icon = cachedPresentation == null ? presentation.getIcon() : cachedPresentation.getIcon();
        textChunks.setIcon(icon == null ? EmptyIcon.ICON_16 : icon);
        textChunks.append("").appendTextPadding(JBUIScale.scale(16 + 5));
        for (int i = 1; i < text.length; i++) {
          TextChunk chunk = text[i];
          textChunks.append(chunk.getText(), getAttributes(isSelected, fileBgColor, selectionBg, selectionFg, chunk));
        }
        SpeedSearchUtil.applySpeedSearchHighlighting(list, textChunks, false, isSelected);

        panel.add(textChunks);
        panel.getAccessibleContext().setAccessibleName(IdeBundle.message("ShowUsagesTableCellRenderer.accessible.USAGE_TEXT_COL",
                                                                         textChunks.getAccessibleContext().getAccessibleName()));

        if (isOriginUsage) {
          SimpleColoredComponent origin;

          if (ExperimentalUI.isNewUI()) {
            RoundedColoredComponent roundedOrigin = new RoundedColoredComponent(isSelected);
            roundedOrigin.append(FindBundle.message("show.usages.current.usage.label"));
            origin = roundedOrigin;
          }
          else {
            origin = new SimpleColoredComponent();
            origin.setOpaque(false);
            origin.setIconTextGap(JBUIScale.scale(5)); // for this particular icon it looks better

            // use attributes of "line number" to show "Current" word
            SimpleTextAttributes attributes =
              text.length == 0 ? SimpleTextAttributes.REGULAR_ATTRIBUTES.derive(-1, new Color(0x808080), null, null) :
              getAttributes(isSelected, fileBgColor, selectionBg, selectionFg, text[0]);
            origin.append("| " + FindBundle.message("show.usages.current.usage.label"), attributes);
            origin.appendTextPadding(JBUIScale.scale(45));
          }
          panel.add(origin, BorderLayout.EAST);
          panel.getAccessibleContext()
            .setAccessibleName(panel.getAccessibleContext().getAccessibleName() + ", " + origin.getAccessibleContext().getAccessibleName());
        }
      }
      default -> throw new IllegalStateException("unknown column: " + column);
    }

    return panel;
  }

  private static @NotNull @NlsSafe String getAccessibleNameForRow(JTable table, int row, boolean isOriginUsage) {
    AccessibleTable accessibleTable = table.getAccessibleContext().getAccessibleTable();
    if (accessibleTable == null) return "";
    int columnCount = accessibleTable.getAccessibleColumnCount();
    StringBuilder str = new StringBuilder();

    if (!ExperimentalUI.isNewUI()) {
      if (isOriginUsage) str.append(IdeBundle.message("ShowUsagesTableCellRenderer.accessible.CURRENT_ASTERISK_COL"));
      else str.append(IdeBundle.message("ShowUsagesTableCellRenderer.accessible.OTHER_ASTERISK_COL"));
      str.append(", ");
    }

    for (int i = 1; i < columnCount; i++) {
      Accessible accessibleItem = accessibleTable.getAccessibleAt(row, i);
      if (accessibleItem == null) continue;
      AccessibleContext accessibleItemContext = accessibleItem.getAccessibleContext();
      if (accessibleItemContext == null) continue;
      String name = accessibleItemContext.getAccessibleName();
      if (name != null) {
        str.append(name);
        str.append(", ");
      }
    }
    return str.toString();
  }

  private static Color slightlyDifferentColor(Color back) {
    if (back == null) {
      return null;
    }

    return EditorColorsManager.getInstance().isDarkEditor() ?
           ColorUtil.brighter(back, 3) : // dunno, under the dark theme the "brighter,1" doesn't look bright enough so we use 3
           ColorUtil.hackBrightness(back, 1, 1 / 1.05f); // Olga insisted on very-pale almost invisible gray. oh well
  }

  private static @NotNull SimpleTextAttributes getAttributes(boolean isSelected,
                                                             Color fileBgColor,
                                                             Color selectionBg,
                                                             Color selectionFg,
                                                             @NotNull TextChunk chunk) {
    SimpleTextAttributes background = chunk.getSimpleAttributesIgnoreBackground();
    return isSelected
           ? new SimpleTextAttributes(selectionBg, selectionFg, null, background.getStyle())
           : deriveBgColor(background, fileBgColor);
  }

  private static @NotNull JComponent textComponentSpanningWholeRow(
    @NotNull SimpleColoredComponent chunks,
    Color rowBackground,
    Color rowSelectionBackground,
    Color rowForeground,
    final int column,
    final @NotNull JTable table
  ) {
    final SimpleColoredComponent component = new SimpleColoredComponent() {
      @Override
      protected void doPaint(Graphics2D g) {
        int leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get();
        Insets innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets();
        int offset = column > 0 && ExperimentalUI.isNewUI() ? -innerInsets.left - leftRightInset : 0;
        int i = 0;
        final TableColumnModel columnModel = table.getColumnModel();
        while (i < column) {
          offset += columnModel.getColumn(i).getWidth();
          i++;
        }
        g.translate(-offset, 0);

        // should increase the column width so that selection background will be visible even after offset translation
        setSize(getWidth() + offset, getHeight());

        super.doPaint(g);

        g.translate(+offset, 0);
      }

      @Override
      public @NotNull Dimension getPreferredSize() {
        //return super.getPreferredSize();
        return column == table.getColumnModel().getColumnCount() - 1 ? super.getPreferredSize() : new Dimension(0, 0);
        // it should span the whole row, so we can't return any specific value here,
        // because otherwise it would be used in the "max width" calculation in com.intellij.find.actions.ShowUsagesAction.calcMaxWidth
      }
    };

    component.setForeground(rowForeground);
    component.setOpaque(false);

    for (SimpleColoredComponent.ColoredIterator iterator = chunks.iterator(); iterator.hasNext(); ) {
      iterator.next();
      String fragment = iterator.getFragment();
      SimpleTextAttributes attributes = iterator.getTextAttributes();
      attributes = attributes.derive(attributes.getStyle(), rowForeground, rowBackground, attributes.getWaveColor());
      component.append(fragment, attributes);
    }

    SelectablePanel result = SelectablePanel.wrap(component);
    applyBackground(result, column, rowBackground, rowSelectionBackground);

    return result;
  }

  private static @NotNull SimpleTextAttributes deriveBgColor(@NotNull SimpleTextAttributes attributes, @Nullable Color fileBgColor) {
    if (fileBgColor != null) {
      attributes = attributes.derive(-1, null, fileBgColor, null);
    }
    return attributes;
  }

  private static void appendGroupText(
    @NotNull JTable table,
    final GroupNode node,
    @NotNull JPanel panel,
    Color fileBgColor,
    boolean isSelected
  ) {
    UsageGroup group = node == null ? null : node.getGroup();
    if (group == null) return;
    GroupNode parentGroup = (GroupNode)node.getParent();
    appendGroupText(table, parentGroup, panel, fileBgColor, isSelected);
    SimpleColoredComponent renderer = new SimpleColoredComponent();
    renderer.setOpaque(false);
    renderer.setIcon(group.getIcon());
    SimpleTextAttributes attributes = deriveBgColor(group.getTextAttributes(isSelected), fileBgColor);
    String text = group.getPresentableGroupText();
    if (isPath(text)) {
      renderer.appendWithClipping(text, attributes, PathTextClipping.getInstance());
      Dimension minSize = renderer.getMinimumSize();
      minSize.width = 50;
      renderer.setMinimumSize(minSize);
    }
    else {
      renderer.append(group.getPresentableGroupText(), attributes);
    }
    SpeedSearchUtil.applySpeedSearchHighlighting(table, renderer, false, isSelected);
    renderer.setMaximumSize(renderer.getPreferredSize());
    panel.add(renderer);
    panel.getAccessibleContext().setAccessibleName(IdeBundle.message("ShowUsagesTableCellRenderer.accessible.FILE_GROUP_COL", renderer.getAccessibleContext().getAccessibleName()));
  }

  private static boolean isPath(String text) {
    return text.chars().filter(ch -> ch == '/').count() > 1;
  }

  /**
   * @param rowBackground background of unselected row
   * @param rowSelectionBackground background of selection if the row is selected or null otherwise
   */
  private static void applyBackground(SelectablePanel panel, int column, Color rowBackground, Color rowSelectionBackground) {
    if (ExperimentalUI.isNewUI()) {
      int leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get();
      Insets innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets();
      switch (column) {
        case CURRENT_ASTERISK_COL -> {
          panel.setSelectionArc(JBUI.CurrentTheme.Popup.Selection.ARC.get());
          panel.setSelectionArcCorners(SelectablePanel.SelectionArcCorners.LEFT);
          //noinspection UseDPIAwareInsets
          panel.setSelectionInsets(new Insets(0, leftRightInset, 0, 0));
          //noinspection UseDPIAwareBorders
          panel.setBorder(new EmptyBorder(innerInsets.top, leftRightInset + innerInsets.left, innerInsets.bottom, 0));
        }
        case USAGE_TEXT_COL -> {
          panel.setSelectionArc(JBUI.CurrentTheme.Popup.Selection.ARC.get());
          panel.setSelectionArcCorners(SelectablePanel.SelectionArcCorners.RIGHT);
          //noinspection UseDPIAwareInsets
          panel.setSelectionInsets(new Insets(0, 0, 0, leftRightInset));
          //noinspection UseDPIAwareBorders
          panel.setBorder(new EmptyBorder(innerInsets.top, 0, innerInsets.bottom, innerInsets.right + leftRightInset));
        }
        default ->
          //noinspection UseDPIAwareBorders
          panel.setBorder(new EmptyBorder(innerInsets.top, 0, innerInsets.bottom, 0));
      }
      panel.setBackground(rowBackground);
      panel.setSelectionColor(rowSelectionBackground);
    }
    else {
      if (column == CURRENT_ASTERISK_COL) {
        panel.setBorder(JBUI.Borders.empty(MARGIN, MARGIN, MARGIN, 0));
      }
      else {
        panel.setBorder(JBUI.Borders.empty(MARGIN, 0));
      }
      panel.setBackground(rowSelectionBackground == null ? rowBackground : rowSelectionBackground);
    }
  }

  private static final int ARC = 8;
  private static final int LEFT_OFFSET = 6;

  private static final class RoundedColoredComponent extends SimpleColoredComponent {

    private RoundedColoredComponent(boolean isSelected) {
      if (isSelected) {
        setOpaque(false);
      }
      JBInsets insets = rectInsets();
      setFont(JBFont.medium());
      setIpad(JBUI.insets(0, LEFT_OFFSET + insets.left, 0, insets.right));
      setForeground(JBUI.CurrentTheme.List.Tag.FOREGROUND);
    }

    @Override
    protected void paintBackground(Graphics2D g, int x, int width, int height) {
      super.paintBackground(g, x, width, height);

      int y = 0;
      int baseline = getBaseline(width, height);
      if (baseline >= 0) {
        JBInsets insets = rectInsets();
        FontMetrics metrics = g.getFontMetrics(getBaseFont());
        y = baseline - metrics.getAscent() - insets.top;
        height = metrics.getHeight() + insets.height();
      }

      Graphics2D g2 = (Graphics2D)g.create();
      try {
        GraphicsUtil.setupAAPainting(g2);
        g2.setColor(JBUI.CurrentTheme.List.Tag.BACKGROUND);
        int arc = JBUIScale.scale(ARC);
        int offset = JBUIScale.scale(LEFT_OFFSET);
        g2.fillRoundRect(x + offset, y, width - offset, height, arc, arc);
      }
      finally {
        g2.dispose();
      }
    }

    private static JBInsets rectInsets() {
      return JBUI.insets(1, 6);
    }
  }

  private static final class MyLayout extends BoxLayout {

    public MyLayout(Container target) {
      super(target, BoxLayout.X_AXIS);
    }

    @Override
    public void layoutContainer(Container container) {
      super.layoutContainer(container);
      for (Component component : container.getComponents()) { // align inner components
        Rectangle b = component.getBounds();
        Insets insets = container.getInsets();
        component.setBounds(b.x, b.y, b.width, container.getSize().height - insets.top - insets.bottom);
      }
    }
  }
}
