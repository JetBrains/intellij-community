// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.intention;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.QuickEditHandler;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpFile;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.psi.RegExpAtom;
import org.intellij.lang.regexp.psi.RegExpBackref;
import org.intellij.lang.regexp.psi.RegExpBoundary;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpCharRange;
import org.intellij.lang.regexp.psi.RegExpClass;
import org.intellij.lang.regexp.psi.RegExpClassElement;
import org.intellij.lang.regexp.psi.RegExpClosure;
import org.intellij.lang.regexp.psi.RegExpConditional;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpIntersection;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;
import org.intellij.lang.regexp.psi.RegExpNumber;
import org.intellij.lang.regexp.psi.RegExpOptions;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.intellij.lang.regexp.psi.RegExpPosixBracketExpression;
import org.intellij.lang.regexp.psi.RegExpProperty;
import org.intellij.lang.regexp.psi.RegExpQuantifier;
import org.intellij.lang.regexp.psi.RegExpRecursiveElementVisitor;
import org.intellij.lang.regexp.psi.RegExpSetOptions;
import org.intellij.lang.regexp.psi.RegExpSimpleClass;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_BOLD;
import static com.intellij.ui.SimpleTextAttributes.STYLE_OPAQUE;
import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

public final class ExplainRegExpIntention implements IntentionAction, Iconable, HighPriorityAction {
  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file instanceof RegExpFile;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof RegExpFile regExpFile)) return;
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      PsiElement start = file.findElementAt(selectionModel.getSelectionStart());
      PsiElement end = file.findElementAt(selectionModel.getSelectionEnd() - 1);
      if (start != null && end != null) {
        PsiElement parent = (start == end && start.getFirstChild() == null) ? start.getParent() : PsiTreeUtil.findCommonParent(start, end);
        QuickEditHandler.showBalloon(editor, file, createBalloonComponent(parent, editor));
        return;
      }
    }
    QuickEditHandler.showBalloon(editor, file, createBalloonComponent(regExpFile, editor));
  }

  private static JComponent createBalloonComponent(PsiElement element, Editor editor) {
    final TreeNode root = buildExplanationTree(element);
    Tree tree = new Tree(root) {
      @Override
      public void addNotify() {
        super.addNotify();
        IdeFocusManager.getGlobalInstance().requestFocus(this, true);
      }

      @Override
      public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (getSelectionCount() > 1 && value instanceof RegExpTreeNode node) {
          int depth = -1;
          RegExpTreeNode parent = node.getParent();
          while (parent != null) {
            depth++;
            parent = parent.getParent();
          }
          return StringUtil.repeat("  ", depth) + (expanded ? "▼ " : "▶ ")
                 + super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
        }
        else {
          return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
        }
      }
    };
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        resize();
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        resize();
      }

      private void resize() {
        Balloon balloon = JBPopupFactory.getInstance().getParentBalloonFor(tree);
        if (balloon != null && !balloon.isDisposed()) balloon.revalidate();
      }
    });
    tree.setFont(editor.getColorsScheme().getFont(EditorFontType.PLAIN));
    tree.setRootVisible(false);
    tree.setAdditionalRowsCount(0);
    ColoredTreeCellRenderer renderer = new ColoredTreeCellRenderer() {

      @Override
      protected void doPaintFragmentBackground(@NotNull Graphics2D g,
                                               int index,
                                               @NotNull Color bgColor,
                                               int x,
                                               int y,
                                               int width,
                                               int height) {
        if (!mySelected) super.doPaintFragmentBackground(g, index, bgColor, x, y, width + 2, height);
      }

      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof RegExpTreeNode node) {
          Value nodeValue = node.getUserObject();
          for (Fragment fragment : nodeValue.pattern()) {
            append(fragment.text(), fragment.attributes());
          }
          Name nameNode = nodeValue.name();
          String name = nameNode.name();
          String explanation = nodeValue.explanation();
          if (!name.isEmpty() || !explanation.isEmpty()) append(" ");
          if (!name.isEmpty()) {
            append(name, LINK_PLAIN_ATTRIBUTES, Registry.is("explain.regexp.intention.enable.info.links") ? nameNode.url() : null);
          }
          if (!explanation.isEmpty()) {
            if (!name.isEmpty()) append(" ", REGULAR_ATTRIBUTES);
            appendHTML(explanation, REGULAR_ATTRIBUTES);
          }
        }
      }
    };
    tree.setCellRenderer(renderer);
    tree.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        boolean hovering = row >= 0
                           && renderer.getFragmentTag(renderer.findFragmentAt(e.getX() - tree.getRowBounds(row).x)) instanceof String url
                           && !url.isEmpty();
        tree.setCursor(hovering ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
      }
    });
    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row < 0) return;
        Object tag = renderer.getFragmentTag(renderer.findFragmentAt(e.getX() - tree.getRowBounds(row).x));
        if (tag instanceof String url && !url.isEmpty()) {
          BrowserUtil.browse(url);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        tree.setCursor(Cursor.getDefaultCursor());
      }
    });
    TreeUtil.expand(tree, new TreeVisitor() {
      @Override
      public @NotNull Action visit(@NotNull TreePath path) {
        Value component = ((RegExpTreeNode)path.getLastPathComponent()).getUserObject();
        return component == null || component.expand() ? Action.CONTINUE : Action.SKIP_CHILDREN;
      }
    }, _ -> {});
    JBScrollPane pane = new JBScrollPane(tree);
    pane.setBorder(JBUI.Borders.empty());
    pane.setPreferredSize(clamp(tree.getPreferredSize()));
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        pane.setPreferredSize(clamp(tree.getPreferredSize()));
        tree.scrollRowToVisible(0);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        pane.setPreferredSize(clamp(tree.getPreferredSize()));
      }
    });
    return pane;
  }

  private static Dimension clamp(Dimension dimension) {
    return new Dimension(Math.min(dimension.width + UIUtil.getScrollBarWidth(), 1024), Math.min(dimension.height, 512));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return RegExpBundle.message("intention.family.name.explain.regular.expression");
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.FileTypes.Regexp;
  }

  public static TreeNode buildExplanationTree(PsiElement element) {
    assert element.getLanguage().isKindOf(RegExpLanguage.INSTANCE);
    ExplanationVisitor visitor = new ExplanationVisitor();
    element.accept(visitor);
    return visitor.getExplanationTree();
  }
}
record Value(
  @NotNull List<Fragment> pattern,
  @NotNull Name name,
  @NotNull @DetailedDescription String explanation,
  boolean expand
) {
  @Override
  public @NotNull String toString() {
    String nameString = name.toString();
    String s = nameString.isEmpty() ? StringUtil.join(pattern, "") : StringUtil.join(pattern, "") + ' ' + name;
    return !explanation.isEmpty() ? s + " – " + StringUtil.stripHtml(explanation, false) : s;
  }
}
record Name(@NotNull @NlsContexts.ColumnName String name, @NotNull @NonNls String url) {
  @Override
  public String toString() {
    return !url.isEmpty() && Registry.is("explain.regexp.intention.enable.info.links") ? name + " (" + url + ')' : name;
  }
}
record Fragment(@NotNull @NlsSafe String text, @NotNull SimpleTextAttributes attributes) {
  @Override
  public @NotNull String toString() {
    return text;
  }
}
class RegExpTreeNode extends DefaultMutableTreeNode {
  RegExpTreeNode(@Nullable Value value) {
    super(value);
  }

  @Override
  public Value getUserObject() {
    return (Value)super.getUserObject();
  }

  @Override
  public RegExpTreeNode getParent() {
    return (RegExpTreeNode)super.getParent();
  }
}
class ExplanationVisitor extends RegExpRecursiveElementVisitor {

  private static final Name EMPTY_NAME = new Name("", "");
  private final SimpleTextAttributes HIGHLIGHT_ATTRIBUTES;
  private final SimpleTextAttributes CODE_ATTRIBUTES;
  private final RegExpTreeNode root = new RegExpTreeNode(null);
  private RegExpTreeNode current = root;
  private int currentGroup = 1;
  private boolean charGroup = false;

  ExplanationVisitor() {
    TextAttributes attributes = EditorColorsUtil.getGlobalOrDefaultColorScheme().getAttributes(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES);
    HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(attributes.getBackgroundColor(), null, null, STYLE_BOLD | STYLE_OPAQUE);
    CODE_ATTRIBUTES = new SimpleTextAttributes(attributes.getBackgroundColor(), NamedColorUtil.getInactiveTextColor(),
                                               null, STYLE_PLAIN | STYLE_OPAQUE);
  }

  public TreeNode getExplanationTree() {
    return root;
  }

  private void leaf(@NotNull PsiElement element, @NotNull Name name, @NotNull @DetailedDescription String explanation) {
    current.insert(new RegExpTreeNode(new Value(buildPatternFragments(element, true), name, explanation, false)), current.getChildCount());
  }

  private void branch(@NotNull PsiElement element, @NotNull Name name, @NotNull @DetailedDescription String explanation) {
    branch(element, name, explanation, true);
  }

  private void branch(@NotNull PsiElement element, @NotNull Name name, @NotNull @DetailedDescription String explanation, boolean expand) {
    branch(new Value(buildPatternFragments(element, true), name, explanation, expand));
  }

  private void branch(Value value) {
    RegExpTreeNode node = new RegExpTreeNode(value);
    current.insert(node, current.getChildCount());
    current = node;
  }

  private @NotNull List<Fragment> buildPatternFragments(@NotNull PsiElement element, boolean emphasize) {
    return buildPatternFragments(element, emphasize, new SmartList<>());
  }

  private List<Fragment> buildPatternFragments(PsiElement element, boolean emphasize, List<Fragment> list) {
    PsiElement child = element.getFirstChild();
    if (child == null || child == element.getLastChild()) {
      list.add(new Fragment(element instanceof RegExpElement e ? e.getUnescapedText() : element.getText(), 
                            emphasize ? HIGHLIGHT_ATTRIBUTES : CODE_ATTRIBUTES));
    }
    else {
      while (child != null) {
        if (child.getFirstChild() == null) {
          list.add(new Fragment(child instanceof RegExpElement e ? e.getUnescapedText() : child.getText(),
                                emphasize ? HIGHLIGHT_ATTRIBUTES : CODE_ATTRIBUTES));
        }
        else {
          boolean keepEmphasis = element instanceof RegExpCharRange
                                 || element instanceof RegExpBranch
                                 || element instanceof RegExpQuantifier
                                 || element instanceof RegExpConditional && (child instanceof RegExpBackref
                                                                             || child instanceof RegExpNamedGroupRef);
          buildPatternFragments(child, keepEmphasis && emphasize, list);
        }
        child = child.getNextSibling();
      }
    }
    return list;
  }

  private static String numText(RegExpNumber num, String whenNull) {
    if (num == null) return whenNull;
    Number value = num.getValue();
    return (value == null) ? "<unknown>" : String.valueOf(value.longValue());
  }

  private void parent() {
    current = current.getParent();
  }

  @Override
  public void visitRegExpPattern(RegExpPattern pattern) {
    RegExpBranch[] branches = pattern.getBranches();
    if (branches.length != 1) {
      Name name = new Name("Alternation", "https://www.regular-expressions.info/alternation.html");
      branch(pattern, name, "matches 1 of " + branches.length + " alternatives");
      super.visitRegExpPattern(pattern);
      parent();
    }
    else {
      super.visitRegExpPattern(pattern);
    }
  }

  @Override
  public void visitRegExpBranch(RegExpBranch branch) {
    if (!(branch.getParent() instanceof RegExpPattern pattern)
        || pattern.getBranches().length > 1
        || pattern.getParent() instanceof RegExpFile) {
      PsiElement[] children = branch.getChildren();
      if (children.length > 1) {
        boolean allSimpleChars = true;
        for (PsiElement child : children) {
          if (!isSimpleChar(child)) {
            allSimpleChars = false;
            break;
          }
        }
        if (!allSimpleChars) {
          branch(new Value(buildPatternFragments(branch, false), EMPTY_NAME, "matches elements in order", true));
          super.visitRegExpBranch(branch);
          parent();
          return;
        }
      }
    }
    super.visitRegExpBranch(branch);
  }

  @Override
  public void visitRegExpQuantifier(RegExpQuantifier quantifier) {
    super.visitRegExpQuantifier(quantifier);
    if (Registry.is("explain.regexp.intention.nested.quantifiers")) return;
    Name node = new Name("Quantifier", "https://www.regular-expressions.info/repeat.html");
    leaf(quantifier, node, quantifierText(quantifier, "matches the previous element "));
  }

  @Override
  public void visitRegExpClosure(RegExpClosure closure) {
    if (!Registry.is("explain.regexp.intention.nested.quantifiers")) {
      super.visitRegExpClosure(closure);
    }
    else {
      Name name = new Name("Quantifier", "https://www.regular-expressions.info/repeat.html");
      branch(closure, name, quantifierText(closure.getQuantifier(), "matches "));
      super.visitRegExpClosure(closure);
      parent();
    }
  }

  private static @NotNull String quantifierText(RegExpQuantifier quantifier, String explanation) {
    boolean addSuffix = true;
    if (quantifier.isCounted()) {
      String min = numText(quantifier.getMin(), "0");
      String max = numText(quantifier.getMax(), null);
      if (max == null) {
        explanation += min + " or more times";
      }
      else {
        if (min.equals(max)) {
          explanation += "exactly " + min + " times";
          addSuffix = false;
        }
        else {
          explanation += "between " + min + " and " + max + " times";
        }
      }
    }
    else {
      ASTNode token = quantifier.getToken();
      assert token != null;
      String tokenText = token.getText();
      if (tokenText.equals("?")) {
        explanation += "zero or one time";
      }
      else if (tokenText.equals("*")) {
        explanation += "zero or more times";
      }
      else if (tokenText.equals("+")) {
        explanation += "one or more times";
      }
      else {
        assert false;
      }
    }
    if (addSuffix) {
      if (quantifier.isReluctant()) {
        explanation += ", as few times as possible";
      }
      else {
        explanation += ", as many times as possible";
      }
    }
    if (quantifier.isPossessive()) {
      explanation += ", without backtracking";
    }
    return explanation;
  }

  @Override
  public void visitSimpleClass(RegExpSimpleClass simpleClass) {
    super.visitSimpleClass(simpleClass);
    RegExpSimpleClass.Kind kind = simpleClass.getKind();
    switch (kind) {
      case ANY -> {
        leaf(simpleClass, new Name("Dot", "https://www.regular-expressions.info/dot.html"),
             "matches any character (excludes line breaks depending on the matching mode)");
      }
      case DIGIT -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html"),
             "matches a digit");
      }
      case NON_DIGIT -> {
        leaf(simpleClass, new Name("Negated Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#negated"),
             "matches a non-digit");
      }
      case WORD -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html"),
             "matches a word character (letter, digit or underscore)");
      }
      case NON_WORD -> {
        leaf(simpleClass, new Name("Negated Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#negated"),
             "matches a non-word character");
      }
      case SPACE -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html"),
             "matches a whitespace character");
      }
      case NON_SPACE -> {
        leaf(simpleClass, new Name("Negated Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#negated"),
             "matches a non-whitespace character");
      }
      case HORIZONTAL_SPACE -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#more"),
             "matches a horizontal whitespace character: [ \\t\\u00A0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000]"
        );
      }
      case NON_HORIZONTAL_SPACE -> {
        leaf(simpleClass, new Name("Negated Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#negated"),
             "matches a non-horizontal whitespace character: [^ \\t\\u00A0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000]");
      }
      case VERTICAL_SPACE -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#more"),
             "matches a vertical whitespace character: [\\n\\x0B\\f\\r\\x85&#92;u2028&#92;u2029]");
      }
      case NON_VERTICAL_SPACE -> {
        leaf(simpleClass, new Name("Negated Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#negated"),
             "matches a non-vertical whitespace character: [^\\n\\x0B\\f\\r\\x85&#92;u2028&#92;u2029]");
      }
      case XML_NAME_START -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#xml"),
             "matches a character that is allowed to be used as the first character of an XML name");
      }
      case NON_XML_NAME_START -> {
        leaf(simpleClass, new Name("Negated Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#xml"),
             "matches a character that is not allowed to be used as the first character of an XML name");
      }
      case XML_NAME_PART -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#xml"),
             "matches a character that is allowed to be part of an XML name after the first character");
      }
      case NON_XML_NAME_PART -> {
        leaf(simpleClass, new Name("Negated Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html#xml"),
             "matches a character that is not allowed to be part of an XML name after the first character");
      }
      case UNICODE_GRAPHEME -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/unicodechars.html#grapheme"),
             "matches any Unicode grapheme (can consist of multiple code points; includes line breaks)");
      }
      case UNICODE_LINEBREAK -> {
        leaf(simpleClass, new Name("Shorthand Character Class", "https://www.regular-expressions.info/shorthand.html"),
             "matches a Unicode line break");
      }
    }
  }

  @Override
  public void visitRegExpClass(RegExpClass regExpClass) {
    Name name = regExpClass.isNegated()
                ? new Name("Negated Character Class", "https://www.regular-expressions.info/charclass.html#negated")
                : new Name("Character Class", "https://www.regular-expressions.info/charclass.html");
    RegExpClassElement[] elements = regExpClass.getElements();
    if (elements.length == 1 && elements[0] instanceof RegExpChar c) {
      // single character case
      branch(regExpClass, name, regExpClass.isNegated()
                                         ? "matches 1 character that is not the " + charText(c) + " character"
                                         : "matches the " + charText(c) + " character", false);
    }
    else if (elements.length == 1 && elements[0] instanceof RegExpCharRange range) {
      branch(regExpClass, name, regExpClass.isNegated() ? "matches 1 character not " + rangeText(range)
                                                        : "matches 1 character " + rangeText(range), false);
    }
    else {
      branch(regExpClass, name, regExpClass.isNegated() ? "matches 1 character not " + "in the set"
                                                        : "matches 1 character " + "in the set");
    }
    super.visitRegExpClass(regExpClass);
    parent();
  }

  @Override
  public void visitRegExpIntersection(RegExpIntersection intersection) {
    branch(intersection, new Name("Character Class Intersection", "https://www.regular-expressions.info/charclassintersect.html"),
           "matches 1 character that matches both the left- and the right-hand side");
    super.visitRegExpIntersection(intersection);
    parent();
  }

  @Override
  public void visitRegExpCharRange(RegExpCharRange range) {
    leaf(range, new Name("Range", "https://www.regular-expressions.info/charclass.html"), "matches 1 character " + rangeText(range));
  }

  private static @Nls @NotNull String rangeText(RegExpCharRange range) {
    RegExpChar from = range.getFrom();
    RegExpChar to = range.getTo();
    return "from " + charText(from) + " to " + charText(to) + " (" + (to.getValue() - from.getValue() + 1) + " characters)";
  }

  @Override
  public void visitRegExpChar(RegExpChar c) {
    super.visitRegExpChar(c);
    if (!charGroup) {
      List<Fragment> pattern = createSimpleCharSequence(c);
      if (pattern != null) {
        charGroup = true;
        branch(new Value(pattern, EMPTY_NAME, "matches characters in order", false));
      }
    }
    Name name = switch (c.getType()) {
      case CHAR -> EMPTY_NAME;
      case HEX -> new Name("Hexadecimal Escape", "https://www.regular-expressions.info/nonprint.html");
      case OCT -> new Name("Octal Escape", "https://www.regular-expressions.info/nonprint.html#octal");
      case UNICODE -> new Name("Unicode Escape", "https://www.regular-expressions.info/nonprint.html");
      case NAMED -> new Name("Named Character", "");
      case CONTROL -> new Name("Control Character Escape", "https://www.regular-expressions.info/nonprint.html");
      case ESCAPE -> new Name("Escape Character", "https://www.regular-expressions.info/nonprint.html");
    };
    leaf(c, name, "matches the " + charText(c) + " character");
    if (charGroup && !isSimpleChar(c.getNextSibling())) {
      charGroup = false;
      parent();
    }
  }

  private List<Fragment> createSimpleCharSequence(RegExpChar c) {
    if (!isSimpleChar(c) || c.getParent() instanceof RegExpClass || isSimpleChar(c.getPrevSibling())) {
      return null;
    }
    PsiElement next = c.getNextSibling();
    if (!isSimpleChar(next)) {
      return null;
    }
    List<Fragment> result = new SmartList<>();
    result.add(new Fragment(c.getUnescapedText(), HIGHLIGHT_ATTRIBUTES));
    while (isSimpleChar(next)) {
      result.add(new Fragment(((RegExpChar)next).getUnescapedText(), HIGHLIGHT_ATTRIBUTES));
      next = next.getNextSibling();
    }
    return result;
  }

  private static @NotNull @Nls String charText(RegExpChar c) {
    int value = c.getValue();
    return c.getType() == RegExpChar.Type.CHAR || !isVisibleCodePoint(value)
           ? Character.getName(value)
           : Character.getName(value) + " (" + Character.toString(value) + ')';
  }

  private static boolean isVisibleCodePoint(int c) {
    if (Character.isWhitespace(c) || c == '\u00A0' || c == '\u2007' || c == '\u3164' || c == '\u202F'/* non-breaking space */) return false;
    return switch (Character.getType(c)) {
      case Character.CONTROL,       // \p{Cc}
           Character.FORMAT,        // \p{Cf}
           Character.PRIVATE_USE,   // \p{Co}
           Character.SURROGATE,     // \p{Cs}
           Character.UNASSIGNED,    // \p{Cn}
           Character.LINE_SEPARATOR,
           Character.PARAGRAPH_SEPARATOR ->
        false;
      case Character.SPACE_SEPARATOR -> false;
      default -> true;
    };
  }

  private static boolean isSimpleChar(PsiElement element) {
    return element instanceof RegExpChar c
           && c.getType() == RegExpChar.Type.CHAR
           && c.getUnescapedText().charAt(0) != '\\'
           && isVisibleCodePoint(c.getValue());
  }

  @Override
  public void visitRegExpGroup(RegExpGroup group) {
    switch (group.getType()) {
      case POSITIVE_LOOKAHEAD -> {
        branch(group, new Name(getLookaroundName(group), "https://www.regular-expressions.info/lookaround.html"),
               "succeeds when the input matches, without becoming part of the result");
      }
      case NEGATIVE_LOOKAHEAD -> {
        branch(group, new Name(getLookaroundName(group), "https://www.regular-expressions.info/lookaround.html"),
               "succeeds when the input does not match, without becoming part of the result");
      }
      case POSITIVE_LOOKBEHIND -> {
        branch(group, new Name(getLookaroundName(group), "https://www.regular-expressions.info/lookaround.html"),
               "succeeds when the previous input matches without becoming part of the result");
      }
      case NEGATIVE_LOOKBEHIND -> {
        branch(group, new Name(getLookaroundName(group), "https://www.regular-expressions.info/lookaround.html"),
               "succeeds when the previous input does not match without becoming part of the result");
      }
      case QUOTED_NAMED_GROUP, PYTHON_NAMED_GROUP, NAMED_GROUP -> {
        branch(group, new Name("Named Capturing Group", "https://www.regular-expressions.info/named.html"), 
               "<b>" + group.getGroupName() + "</b> stores the text it matches for later reference");
      }
      case CAPTURING_GROUP -> {
        branch(group, new Name("Capturing Group", "https://www.regular-expressions.info/brackets.html"),
               "<b>#" + currentGroup + "</b> stores the text it matches for later reference");
        currentGroup++;
      }
      case NON_CAPTURING -> {
        branch(group, new Name("Non-Capturing Group", "https://www.regular-expressions.info/brackets.html#noncapture"), 
               "used for optimization when capturing is not needed");
      }
      case ATOMIC -> {
        branch(group, new Name("Atomic Group", "https://www.regular-expressions.info/atomic.html"), 
               "does not backtrack after it matches");
      }
      case PCRE_BRANCH_RESET -> {
        branch(group, new Name("Branch Reset Group", "https://www.regular-expressions.info/branchreset.html"),
               "resets branch numbering between alternatives inside");
      }
      case OPTIONS -> {
        branch(group, new Name("Inline Modifier Group", "https://www.regular-expressions.info/modifiers.html"),
               "turns a regex mode on or off for the pattern inside");
      }
    }
    super.visitRegExpGroup(group);
    parent();
  }

  @Override
  public void visitRegExpConditional(RegExpConditional conditional) {
    RegExpAtom condition = conditional.getCondition();
    String explanation;
    if (condition instanceof RegExpGroup group) {
      String name = getLookaroundName(group);
      explanation = conditional.getElseBranch() == null
                    ? "matches depending on whether " + name + " succeeds"
                    : "matches one of two alternatives based on whether " + name + " succeeds";
    }
    else if (condition instanceof RegExpNamedGroupRef ref) {
      String name = "Named Capturing Group <b>" + ref.getGroupName() + "</b>";
      explanation = conditional.getElseBranch() == null
                    ? "matches depending on whether " + name + " matches"
                    : "matches one of two alternatives based on whether " + name + " matches";
    }
    else if (condition instanceof RegExpBackref ref) {
      String name = "Capturing Group <b>#" + ref.getIndex() + "</b>";
      explanation = conditional.getElseBranch() == null
                    ? "matches depending on whether " + name + " matches"
                    : "matches one of two alternatives based on whether " + name + " matches";
    }
    else {
      explanation = "incomplete expression";
    }
    branch(conditional, new Name("Conditional", "https://www.regular-expressions.info/conditional.html"), explanation);
    if (condition instanceof RegExpGroup) {
      super.visitRegExpConditional(conditional);
    }
    else {
      RegExpBranch thenBranch = conditional.getThenBranch();
      if (thenBranch != null) {
        visitRegExpBranch(thenBranch);
      }
      RegExpBranch elseBranch = conditional.getElseBranch();
      if (elseBranch != null) {
        visitRegExpBranch(elseBranch);
      }
    }
    parent();
  }

  private static @NlsContexts.ColumnName String getLookaroundName(RegExpGroup group) {
    return switch (group.getType()) {
      case POSITIVE_LOOKAHEAD -> "Positive Lookahead Assertion";
      case NEGATIVE_LOOKAHEAD -> "Negative Lookahead Assertion";
      case POSITIVE_LOOKBEHIND -> "Positive Lookbehind Assertion";
      case NEGATIVE_LOOKBEHIND -> "Negative Lookbehind Assertion";
      default -> throw new AssertionError();
    };
  }

  @Override
  public void visitRegExpSetOptions(RegExpSetOptions options) {
    branch(options, new Name("Inline Mode Modifier", "https://www.regular-expressions.info/modifiers.html"), "turns regex modes on or off");
    HashSet<Character> seen = new HashSet<>();
    addModeExplanation(options.getOffOptions(), true, seen);
    addModeExplanation(options.getOnOptions(), false, seen);
    parent();
  }

  private void addModeExplanation(RegExpOptions options, boolean off, Set<Character> seen) {
    if (options == null) return;
    String text = options.getText();
    if (text.isEmpty()) return;
    for (int i = text.length() - 1, limit = off ? 1 : 0; i >= limit; i--) {
      char c = text.charAt(i);
      if (!seen.add(c)) continue;
      String mode = switch (c) {
        case 'd' -> "Unix lines";
        case 'i' -> "case-insensitive";
        case 'm' -> "multiline";
        case 's' -> "dotall";
        case 'u' -> "Unicode-aware case folding";
        case 'U' -> "Unicode character class";
        case 'x' -> "comments";
        default -> "<unknown>";
      };
      @NotNull Value value = new Value(List.of(new Fragment("" + c, HIGHLIGHT_ATTRIBUTES)), EMPTY_NAME,
                                               "turns " + (off ? "off " : "on ") + mode + " mode", false);
      current.insert(new RegExpTreeNode(value), 0);
    }
  }

  @Override
  public void visitRegExpBackref(RegExpBackref backref) {
    leaf(backref, new Name("Back Reference", "https://www.regular-expressions.info/backref.html"), 
         "matches the text matched by group <b>#" + backref.getIndex() + "</b> again");
    super.visitRegExpBackref(backref);
  }

  @Override
  public void visitRegExpNamedGroupRef(RegExpNamedGroupRef groupRef) {
    leaf(groupRef, new Name("Named Group Reference", "https://www.regular-expressions.info/named.html"),
         "matches the text matched by group <b>" + groupRef.getGroupName() + "</b> again");
    super.visitRegExpNamedGroupRef(groupRef);
  }

  @Override
  public void visitRegExpProperty(RegExpProperty property) {
    ASTNode categoryNode = property.getCategoryNode();
    if (categoryNode != null) {
      String explanation = RegExpLanguageHosts.getInstance().getPropertyDescription(property, categoryNode.getText());
      if (explanation != null) {
        if (property.isNegated()) {
          leaf(property, new Name("Negated Unicode Property", ""), "matches any non-" + explanation.toLowerCase(Locale.ROOT));
        }
        else {
          leaf(property, new Name("Unicode Property", ""), "matches any " + explanation.toLowerCase(Locale.ROOT));
        }
      }
    }
    super.visitRegExpProperty(property);
  }

  @Override
  public void visitPosixBracketExpression(RegExpPosixBracketExpression posixBracketExpression) {
    super.visitPosixBracketExpression(posixBracketExpression);
    String name = posixBracketExpression.getClassName();
    String explanation = switch (name) {
      case "alnum" -> "alphanumeric character";
      case "alpha" -> "alphabetic character";
      case "ascii" -> "ASCII character";
      case "blank" -> "space or tab character";
      case "cntrl" -> "control character";
      case "digit" -> "numeric digit";
      case "graph" -> "visible (no whitespace or control) character";
      case "lower" -> "lowercase alphabetic character";
      case "print" -> "visible or whitespace (no control) character";
      case "punct" -> "punctuation or symbol character";
      case "space" -> "whitespace or linebreak character";
      case "upper" -> "uppercase alphabetic character";
      case "word" -> "word character (letter, digit or underscore)";
      case "xdigit" -> "hexadecimal digit";
      default -> "<unknown>";
    };
    leaf(posixBracketExpression, new Name("POSIX Bracket Expression", "https://www.regular-expressions.info/posixbrackets.html"),
           "matches any " + explanation);
  }

  @Override
  public void visitRegExpBoundary(RegExpBoundary boundary) {
    switch (boundary.getType()) {
      case LINE_START ->
        leaf(boundary, new Name("Anchor", "https://www.regular-expressions.info/anchors.html"),
             "matches before the start of the input (and after a line terminator in multi-line mode)");
      case LINE_END ->
        leaf(boundary, new Name("Anchor", "https://www.regular-expressions.info/anchors.html"),
             "matches after the end of the input (and before a line terminator in multi-line mode)");
      case WORD ->
        leaf(boundary, new Name("Word Boundary", "https://www.regular-expressions.info/wordboundaries.html"),
             "matches between a word character and a non-word character");
      case UNICODE_EXTENDED_GRAPHEME -> 
        leaf(boundary, new Name("Unicode Grapheme Boundary", "https://www.regular-expressions.info/unicodeboundaries.html#grapheme"),
             "matches between two characters, where one character can consist of multiple code points");
      case NON_WORD ->
        leaf(boundary, new Name("Word non-boundary", "https://www.regular-expressions.info/wordboundaries.html"),
             "matches between 2 word characters or 2 non-word characters");
      case BEGIN ->
        leaf(boundary, new Name("Anchor", "https://www.regular-expressions.info/anchors.html"),
             "matches before the start of the input");
      case END ->
        leaf(boundary, new Name("Anchor", "https://www.regular-expressions.info/anchors.html"),
             "matches after the end of the input");
      case END_NO_LINE_TERM ->
        leaf(boundary, new Name("Anchor", "https://www.regular-expressions.info/anchors.html"),
             "matches after the end of the input, before a final line terminator if any");
      case PREVIOUS_MATCH ->
        leaf(boundary, new Name("Match Anchor", "https://www.regular-expressions.info/continue.html"),
             "matches after the end of the previous match, or at the start of the input on the first attempt");
      case RESET_MATCH -> {
        leaf(boundary, new Name("Reset Match", "https://www.regular-expressions.info/keep.html"),
             "keeps the text matched so far out of the match result");
      }
    }
    super.visitRegExpBoundary(boundary);
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    leaf(comment, new Name("Comment", "https://www.regular-expressions.info/freespacing.html"), "");
    super.visitComment(comment);
  }
}