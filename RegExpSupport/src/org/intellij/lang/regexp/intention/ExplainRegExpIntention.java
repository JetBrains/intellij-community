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
import org.jetbrains.annotations.PropertyKey;

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
          Feature nameNode = nodeValue.name();
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
  @NotNull Feature name,
  @NotNull @Nls String explanation,
  boolean expand
) {
  @Override
  public @NotNull String toString() {
    String nameString = name.toString();
    String s = nameString.isEmpty() ? StringUtil.join(pattern, "") : StringUtil.join(pattern, "") + ' ' + name;
    return !explanation.isEmpty() ? s + " – " + StringUtil.stripHtml(explanation, false) : s;
  }
}
final class Feature {
  private final @Nullable String nameKey;
  private final @NotNull @NonNls String url;

  Feature(@Nullable @PropertyKey(resourceBundle = RegExpBundle.BUNDLE) String nameKey, @NotNull @NonNls String url) {
    this.nameKey = nameKey;
    this.url = url;
  }

  public @NotNull @NlsContexts.ColumnName String name() {
    return nameKey == null ? "" : RegExpBundle.message(nameKey);
  }

  public @NotNull @NonNls String url() {
    return url;
  }

  @Override
  public String toString() {
    return !url.isEmpty() && Registry.is("explain.regexp.intention.enable.info.links") ? name() + " (" + url + ')' : name();
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

  private static final @NonNls String HOST = "https://www.regular-expressions.info/";

  private static final Feature ALTERNATION = new Feature("explain.feature.alternation", HOST + "alternation.html");
  private static final Feature ANCHOR = new Feature("explain.feature.anchor", HOST + "anchors.html");
  private static final Feature ATOMIC_GROUP = new Feature("explain.feature.atomic.group", HOST + "atomic.html");
  private static final Feature BACK_REFERENCE = new Feature("explain.feature.back.reference", HOST + "backref.html");
  private static final Feature BRANCH_RESET_GROUP = new Feature("explain.feature.branch.reset.group", HOST + "branchreset.html");
  private static final Feature CAPTURING_GROUP = new Feature("explain.feature.capturing.group", HOST + "brackets.html");
  private static final Feature CHAR_CLASS = new Feature("explain.feature.character.class", HOST + "charclass.html");
  private static final Feature CHAR_CLASS_INTERSECTION = new Feature("explain.feature.character.class.intersection", HOST + "charclassintersect.html");
  private static final Feature CHAR_RANGE = new Feature("explain.feature.range", HOST + "charclass.html");
  private static final Feature COMMENT = new Feature("explain.feature.comment", HOST + "freespacing.html");
  private static final Feature CONDITIONAL = new Feature("explain.feature.conditional", HOST + "conditional.html");
  private static final Feature CONTROL_CHAR = new Feature("explain.feature.control.character.escape", HOST + "nonprint.html");
  private static final Feature DOT = new Feature("explain.feature.dot", HOST + "dot.html");
  private static final Feature EMPTY = new Feature(null, "");
  private static final Feature ESCAPE_CHAR = new Feature("explain.feature.escape.character", HOST + "nonprint.html");
  private static final Feature GRAPHEME_BOUNDARY = new Feature("explain.feature.unicode.grapheme.boundary", HOST + "unicodeboundaries.html#grapheme");
  private static final Feature GRAPHEME_SHORT_CLASS = new Feature("explain.feature.short.class", HOST + "unicodechars.html#grapheme");
  private static final Feature HEX_ESCAPE = new Feature("explain.feature.hexadecimal.escape", HOST + "nonprint.html");
  private static final Feature INLINE_MODE_MODIFIER = new Feature("explain.feature.inline.mode.modifier", HOST + "modifiers.html");
  private static final Feature INLINE_MODIFIER_GROUP = new Feature("explain.feature.inline.modifier.group", HOST + "modifiers.html");
  private static final Feature MATCH_ANCHOR = new Feature("explain.feature.match.anchor", HOST + "continue.html");
  private static final Feature MORE_SHORT_CLASS = new Feature("explain.feature.short.class", HOST + "shorthand.html#more");
  private static final Feature NAMED_CAPTURING_GROUP = new Feature("explain.feature.named.capturing.group", HOST + "named.html");
  private static final Feature NAMED_CHAR = new Feature("explain.feature.named.character", "");
  private static final Feature NAMED_GROUP_REFERENCE = new Feature("explain.feature.named.group.reference", HOST + "named.html");
  private static final Feature NEGATED_CHAR_CLASS = new Feature("explain.feature.negated.character.class", HOST + "charclass.html#negated");
  private static final Feature NEGATED_SHORT_CLASS = new Feature("explain.feature.negated.short.class", HOST + "shorthand.html#negated");
  private static final Feature NEGATED_UNICODE_PROPERTY = new Feature("explain.feature.negated.unicode.property", "");
  private static final Feature NEGATED_XML_SHORT_CLASS = new Feature("explain.feature.negated.short.class", HOST + "shorthand.html#xml");
  private static final Feature NEGATIVE_LOOKBEHIND = new Feature("explain.feature.negative.lookbehind.assertion", HOST + "lookaround.html");
  private static final Feature NEGATIVE_LOOKAHEAD = new Feature("explain.feature.negative.lookahead.assertion", HOST + "lookaround.html");
  private static final Feature NON_CAPTURING_GROUP = new Feature("explain.feature.non.capturing.group", HOST + "brackets.html#noncapture");
  private static final Feature OCTAL_ESCAPE = new Feature("explain.feature.octal.escape", HOST + "nonprint.html#octal");
  private static final Feature POSITIVE_LOOKAHEAD = new Feature("explain.feature.positive.lookahead.assertion", HOST + "lookaround.html");
  private static final Feature POSITIVE_LOOKBEHIND = new Feature("explain.feature.positive.lookbehind.assertion", HOST + "lookaround.html");
  private static final Feature POSIX_BRACKETS = new Feature("explain.feature.posix.bracket.expression", HOST + "posixbrackets.html");
  private static final Feature QUANTIFIER = new Feature("explain.feature.quantifier", HOST + "repeat.html");
  private static final Feature RESET_MATCH = new Feature("explain.feature.reset.match", HOST + "keep.html");
  private static final Feature SHORT_CLASS = new Feature("explain.feature.short.class", HOST + "shorthand.html");
  private static final Feature UNICODE_ESCAPE = new Feature("explain.feature.unicode.escape", HOST + "nonprint.html");
  private static final Feature UNICODE_PROPERTY = new Feature("explain.feature.unicode.property", "");
  private static final Feature WORD_BOUNDARY = new Feature("explain.feature.word.boundary", HOST + "wordboundaries.html");
  private static final Feature WORD_NON_BOUNDARY = new Feature("explain.feature.word.non.boundary", HOST + "wordboundaries.html");
  private static final Feature XML_SHORT_CLASS = new Feature("explain.feature.short.class", HOST + "shorthand.html#xml");

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

  private void leaf(@NotNull PsiElement element, @NotNull Feature feature, @NotNull @Nls String explanation) {
    current.insert(new RegExpTreeNode(new Value(buildPatternFragments(element, true), feature, explanation, false)), current.getChildCount());
  }

  private void branch(@NotNull PsiElement element, @NotNull Feature feature, @NotNull @Nls String explanation) {
    branch(element, feature, explanation, true);
  }

  private void branch(@NotNull PsiElement element, @NotNull Feature feature, @NotNull @Nls String explanation, boolean expand) {
    branch(new Value(buildPatternFragments(element, true), feature, explanation, expand));
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
                                 || element instanceof RegExpConditional
                                    && (child instanceof RegExpBackref || child instanceof RegExpNamedGroupRef);
          buildPatternFragments(child, keepEmphasis && emphasize, list);
        }
        child = child.getNextSibling();
      }
    }
    return list;
  }

  private static @NlsSafe String numText(RegExpNumber num, @NlsSafe String whenNull) {
    if (num == null) return whenNull;
    Number value = num.getValue();
    return (value == null) ? RegExpBundle.message("explain.unknown") : String.valueOf(value.longValue());
  }

  private void parent() {
    current = current.getParent();
  }

  @Override
  public void visitRegExpPattern(RegExpPattern pattern) {
    RegExpBranch[] branches = pattern.getBranches();
    if (branches.length != 1) {
      branch(pattern, ALTERNATION, RegExpBundle.message("explain.alternation", branches.length));
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
          branch(new Value(buildPatternFragments(branch, false), EMPTY, RegExpBundle.message("explain.branch"), true));
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
    leaf(quantifier, QUANTIFIER, RegExpBundle.message("explain.matches.the.previous.element.times", quantifierText(quantifier)));
  }

  @Override
  public void visitRegExpClosure(RegExpClosure closure) {
    if (!Registry.is("explain.regexp.intention.nested.quantifiers")) {
      super.visitRegExpClosure(closure);
    }
    else {
      branch(closure, QUANTIFIER, RegExpBundle.message("explain.matches.times", quantifierText(closure.getQuantifier())));
      super.visitRegExpClosure(closure);
      parent();
    }
  }

  private static @NotNull @Nls String quantifierText(RegExpQuantifier quantifier) {
    String min;
    String max;
    if (quantifier.isCounted()) {
      min = numText(quantifier.getMin(), "0");
      max = numText(quantifier.getMax(), null);
    }
    else {
      ASTNode token = quantifier.getToken();
      assert token != null;
      String tokenText = token.getText();
      if (tokenText.equals("?")) {
        min = "0";
        max = "1";
      }
      else if (tokenText.equals("*")) {
        min = "0";
        max = null;
      }
      else if (tokenText.equals("+")) {
        min = "1";
        max = null;
      }
      else {
        throw new AssertionError();
      }
    }
    if (!min.equals(max)) {
      String backtracking = quantifier.isPossessive() ? RegExpBundle.message("explain.without.backtracking") : "";
      String suffix = quantifier.isReluctant()
                      ? RegExpBundle.message("explain.as.few.times.as.possible", backtracking)
                      : RegExpBundle.message("explain.as.many.times.as.possible", backtracking);
      return max == null
             ? RegExpBundle.message("explain.n.or.more.times", min, suffix)
             : RegExpBundle.message("explain.between.n.and.m.times", min, max, suffix);
    }
    return RegExpBundle.message("explain.exactly.n.times", min);
  }

  @Override
  public void visitSimpleClass(RegExpSimpleClass simpleClass) {
    super.visitSimpleClass(simpleClass);
    RegExpSimpleClass.Kind kind = simpleClass.getKind();
    switch (kind) {
      case ANY -> leaf(simpleClass, DOT, RegExpBundle.message("explain.dot"));
      case DIGIT -> leaf(simpleClass, SHORT_CLASS, RegExpBundle.message("explain.digit.short.class"));
      case NON_DIGIT -> leaf(simpleClass, NEGATED_SHORT_CLASS, RegExpBundle.message("explain.non.digit.short.class"));
      case WORD -> leaf(simpleClass, SHORT_CLASS, RegExpBundle.message("explain.word.short.class"));
      case NON_WORD -> leaf(simpleClass, NEGATED_SHORT_CLASS, RegExpBundle.message("explain.non.word.short.class"));
      case SPACE -> leaf(simpleClass, SHORT_CLASS, RegExpBundle.message("explain.space.short.class"));
      case NON_SPACE -> leaf(simpleClass, NEGATED_SHORT_CLASS, RegExpBundle.message("explain.non.space.short.class"));
      case HORIZONTAL_SPACE -> leaf(simpleClass, MORE_SHORT_CLASS, RegExpBundle.message("explain.horizontal.space.short.class"));
      case NON_HORIZONTAL_SPACE -> leaf(simpleClass, NEGATED_SHORT_CLASS, RegExpBundle.message("explain.non.horizontal.space.short.class"));
      case VERTICAL_SPACE -> leaf(simpleClass, MORE_SHORT_CLASS, RegExpBundle.message("explain.vertical.space.short.class"));
      case NON_VERTICAL_SPACE -> leaf(simpleClass, NEGATED_SHORT_CLASS, RegExpBundle.message("explain.non.vertical.space.short.class"));
      case XML_NAME_START -> leaf(simpleClass, XML_SHORT_CLASS, RegExpBundle.message("explain.xml.name.start.short.class"));
      case NON_XML_NAME_START -> leaf(simpleClass, NEGATED_XML_SHORT_CLASS, RegExpBundle.message("explain.non.xml.name.start.short.class"));
      case XML_NAME_PART -> leaf(simpleClass, XML_SHORT_CLASS, RegExpBundle.message("explain.xml.name.part.short.class"));
      case NON_XML_NAME_PART -> leaf(simpleClass, NEGATED_XML_SHORT_CLASS, RegExpBundle.message("explain.non.xml.name.part.short.class"));
      case UNICODE_GRAPHEME -> leaf(simpleClass, GRAPHEME_SHORT_CLASS, RegExpBundle.message("explain.unicode.grapheme.short.class"));
      case UNICODE_LINEBREAK -> leaf(simpleClass, SHORT_CLASS, RegExpBundle.message("explain.unicode.line.break.short.class"));
    }
  }

  @Override
  public void visitRegExpClass(RegExpClass regExpClass) {
    Feature name = regExpClass.isNegated() ? NEGATED_CHAR_CLASS : CHAR_CLASS;
    RegExpClassElement[] elements = regExpClass.getElements();
    if (elements.length == 1 && elements[0] instanceof RegExpChar c) {
      // single character case
      var m = RegExpBundle.message(regExpClass.isNegated() ? "explain.negated.single.character" : "explain.single.character", charText(c));
      branch(regExpClass, name, m, false);
    }
    else if (elements.length == 1 && elements[0] instanceof RegExpCharRange range) {
      branch(regExpClass, name, rangeText(range, regExpClass.isNegated()), false);
    }
    else {
      branch(regExpClass, name, RegExpBundle.message(regExpClass.isNegated() ? "explain.negated.class" : "explain.class"));
    }
    super.visitRegExpClass(regExpClass);
    parent();
  }

  @Override
  public void visitRegExpIntersection(RegExpIntersection intersection) {
    branch(intersection, CHAR_CLASS_INTERSECTION, RegExpBundle.message("explain.matches.intersection"));
    super.visitRegExpIntersection(intersection);
    parent();
  }

  @Override
  public void visitRegExpCharRange(RegExpCharRange range) {
    leaf(range, CHAR_RANGE, rangeText(range, false));
  }

  private static @Nls @NotNull String rangeText(RegExpCharRange range, boolean negated) {
    RegExpChar from = range.getFrom();
    RegExpChar to = range.getTo();
    String count = to == null ? RegExpBundle.message("explain.unknown") : String.valueOf(to.getValue() - from.getValue() + 1);
    return RegExpBundle.message(negated ? "explain.negated.range" : "explain.range", charText(from), charText(to), count);
  }

  @Override
  public void visitRegExpChar(RegExpChar c) {
    super.visitRegExpChar(c);
    if (!charGroup) {
      List<Fragment> pattern = createSimpleCharSequence(c);
      if (pattern != null) {
        charGroup = true;
        branch(new Value(pattern, EMPTY, RegExpBundle.message("explain.multiple.characters"), false));
      }
    }
    Feature name = switch (c.getType()) {
      case CHAR -> EMPTY;
      case HEX -> HEX_ESCAPE;
      case OCT -> OCTAL_ESCAPE;
      case UNICODE -> UNICODE_ESCAPE;
      case NAMED -> NAMED_CHAR;
      case CONTROL -> CONTROL_CHAR;
      case ESCAPE -> ESCAPE_CHAR;
    };
    leaf(c, name, RegExpBundle.message("explain.single.character", charText(c)));
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

  private static @NotNull @NlsSafe String charText(RegExpChar c) {
    if (c == null) return RegExpBundle.message("explain.unknown");
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
      case POSITIVE_LOOKAHEAD -> branch(group, POSITIVE_LOOKAHEAD, RegExpBundle.message("explain.positive.lookahead"));
      case NEGATIVE_LOOKAHEAD -> branch(group, NEGATIVE_LOOKAHEAD, RegExpBundle.message("explain.negative.lookahead"));
      case POSITIVE_LOOKBEHIND -> branch(group, POSITIVE_LOOKBEHIND, RegExpBundle.message("explain.positive.lookbehind"));
      case NEGATIVE_LOOKBEHIND -> branch(group, NEGATIVE_LOOKBEHIND, RegExpBundle.message("explain.negtive.lookbehind"));
      case QUOTED_NAMED_GROUP, PYTHON_NAMED_GROUP, NAMED_GROUP ->
        branch(group, NAMED_CAPTURING_GROUP, RegExpBundle.message("explain.named.capturing.group", group.getGroupName()));
      case CAPTURING_GROUP -> branch(group, CAPTURING_GROUP, RegExpBundle.message("explain.capturing.group", currentGroup++));
      case NON_CAPTURING -> branch(group, NON_CAPTURING_GROUP, RegExpBundle.message("explain.non.capturing.group"));
      case ATOMIC -> branch(group, ATOMIC_GROUP, RegExpBundle.message("explain.atomic.group"));
      case PCRE_BRANCH_RESET -> branch(group, BRANCH_RESET_GROUP, RegExpBundle.message("explain.branch.reset.group"));
      case OPTIONS -> branch(group, INLINE_MODIFIER_GROUP, RegExpBundle.message("explain.inline.modifier.group"));
    }
    super.visitRegExpGroup(group);
    parent();
  }

  @Override
  public void visitRegExpConditional(RegExpConditional conditional) {
    RegExpAtom condition = conditional.getCondition();
    String explanation;
    if (condition instanceof RegExpGroup group) {
      String name = switch (group.getType()) {
        case POSITIVE_LOOKAHEAD -> RegExpBundle.message("explain.feature.positive.lookahead.assertion");
        case NEGATIVE_LOOKAHEAD -> RegExpBundle.message("explain.feature.negative.lookahead.assertion");
        case POSITIVE_LOOKBEHIND -> RegExpBundle.message("explain.feature.positive.lookbehind.assertion");
        case NEGATIVE_LOOKBEHIND -> RegExpBundle.message("explain.feature.negative.lookbehind.assertion");
        default -> throw new AssertionError();
      };
      explanation = conditional.getElseBranch() == null
                    ? RegExpBundle.message("explain.conditional.with.lookaround", name)
                    : RegExpBundle.message("explain.conditional.with.lookaround.and.else", name);
    }
    else if (condition instanceof RegExpNamedGroupRef ref) {
      explanation = conditional.getElseBranch() == null
                    ? RegExpBundle.message("explain.conditional.with.named.capturing.group", ref.getGroupName())
                    : RegExpBundle.message("explain.conditional.with.named.capturing.group.and.else", ref.getGroupName());
    }
    else if (condition instanceof RegExpBackref ref) {
      explanation = conditional.getElseBranch() == null
                    ? RegExpBundle.message("explain.conditional.with.capturing.group", ref.getIndex())
                    : RegExpBundle.message("explain.conditional.with.capturing.group.and.else", ref.getIndex());
    }
    else {
      explanation = RegExpBundle.message("explain.unknown");
    }
    branch(conditional, CONDITIONAL, explanation);
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

  @Override
  public void visitRegExpSetOptions(RegExpSetOptions options) {
    branch(options, INLINE_MODE_MODIFIER, RegExpBundle.message("explain.set.options"));
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
      int type = switch (c) {
        case 'd' -> 1;
        case 'i' -> 2;
        case 'm' -> 3;
        case 's' -> 4;
        case 'u' -> 5;
        case 'U' -> 6;
        case 'x' -> 7;
        default -> 0;
      };
      String explanation = RegExpBundle.message(off ? "explain.mode.off.option" : "explain.mode.on.option", type);
      @NotNull Value value = new Value(List.of(new Fragment("" + c, HIGHLIGHT_ATTRIBUTES)), EMPTY, explanation, false);
      current.insert(new RegExpTreeNode(value), 0);
    }
  }

  @Override
  public void visitRegExpBackref(RegExpBackref backref) {
    leaf(backref, BACK_REFERENCE, RegExpBundle.message("explain.backref", backref.getIndex()));
    super.visitRegExpBackref(backref);
  }

  @Override
  public void visitRegExpNamedGroupRef(RegExpNamedGroupRef groupRef) {
    leaf(groupRef, NAMED_GROUP_REFERENCE, RegExpBundle.message("explain.named.group.ref", groupRef.getGroupName()));
    super.visitRegExpNamedGroupRef(groupRef);
  }

  @Override
  public void visitRegExpProperty(RegExpProperty property) {
    ASTNode categoryNode = property.getCategoryNode();
    if (categoryNode != null) {
      String explanation = RegExpLanguageHosts.getInstance().getPropertyDescription(property, categoryNode.getText());
      explanation = explanation == null ? RegExpBundle.message("explain.unknown") : explanation.toLowerCase(Locale.ROOT);
      if (property.isNegated()) {
        leaf(property, NEGATED_UNICODE_PROPERTY, RegExpBundle.message("explain.negated.unicode.property", explanation));
      }
      else {
        leaf(property, UNICODE_PROPERTY, RegExpBundle.message("explain.unicode.property", explanation));
      }
    }
    super.visitRegExpProperty(property);
  }

  @Override
  public void visitPosixBracketExpression(RegExpPosixBracketExpression posixBracketExpression) {
    super.visitPosixBracketExpression(posixBracketExpression);
    String name = posixBracketExpression.getClassName();
    int type = switch (name) {
      case "alnum" -> 1;
      case "alpha" -> 2;
      case "ascii" -> 3;
      case "blank" -> 4;
      //noinspection SpellCheckingInspection
      case "cntrl" -> 5;
      case "digit" -> 6;
      case "graph" -> 7;
      case "lower" -> 8;
      case "print" -> 9;
      //noinspection SpellCheckingInspection
      case "punct" -> 10;
      case "space" -> 11;
      case "upper" -> 12;
      case "word"  -> 13;
      //noinspection SpellCheckingInspection
      case "xdigit" -> 14;
      default -> 0;
    };
    leaf(posixBracketExpression, POSIX_BRACKETS, RegExpBundle.message("explain.posix.bracket.expression", type));
  }

  @Override
  public void visitRegExpBoundary(RegExpBoundary boundary) {
    switch (boundary.getType()) {
      case LINE_START -> leaf(boundary, ANCHOR, RegExpBundle.message("explain.line.start.anchor"));
      case LINE_END -> leaf(boundary, ANCHOR, RegExpBundle.message("explain.line.end.anchor"));
      case WORD -> leaf(boundary, WORD_BOUNDARY, RegExpBundle.message("explain.word.boundary"));
      case UNICODE_EXTENDED_GRAPHEME -> leaf(boundary, GRAPHEME_BOUNDARY, RegExpBundle.message("explain.unicode.extended.grapheme"));
      case NON_WORD -> leaf(boundary, WORD_NON_BOUNDARY, RegExpBundle.message("explain.word.non.boundary"));
      case BEGIN -> leaf(boundary, ANCHOR, RegExpBundle.message("explain.begin.anchor"));
      case END -> leaf(boundary, ANCHOR, RegExpBundle.message("explain.end.anchor"));
      case END_NO_LINE_TERM -> leaf(boundary, ANCHOR, RegExpBundle.message("explain.end.no.line.term.anchor"));
      case PREVIOUS_MATCH -> leaf(boundary, MATCH_ANCHOR, RegExpBundle.message("explain.previous.match.anchor"));
      case RESET_MATCH -> leaf(boundary, RESET_MATCH, RegExpBundle.message("explain.reset.match"));
    }
    super.visitRegExpBoundary(boundary);
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    leaf(comment, COMMENT, "");
    super.visitComment(comment);
  }
}