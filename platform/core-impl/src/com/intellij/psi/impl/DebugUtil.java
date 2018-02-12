// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.graph.InboundSemiGraph;
import com.intellij.util.graph.OutboundSemiGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor", "UnusedDeclaration", "TestOnlyProblems"})
public class DebugUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DebugUtil");

  public static /*final*/ boolean CHECK = false;
  public static final boolean DO_EXPENSIVE_CHECKS;
  static {
    Application application = ApplicationManager.getApplication();
    DO_EXPENSIVE_CHECKS = application != null && application.isUnitTestMode();
  }
  public static final boolean CHECK_INSIDE_ATOMIC_ACTION_ENABLED = DO_EXPENSIVE_CHECKS;

  public static String psiTreeToString(@NotNull final PsiElement element, final boolean skipWhitespaces) {
    final ASTNode node = SourceTreeToPsiMap.psiElementToTree(element);
    assert node != null : element;
    return treeToString(node, skipWhitespaces);
  }

  public static String treeToString(@NotNull final ASTNode root, final boolean skipWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, skipWhitespaces, false, false, true);
    return buffer.toString();
  }

  public static String nodeTreeToString(@NotNull final ASTNode root, final boolean skipWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, skipWhitespaces, false, false, false);
    return buffer.toString();
  }

  public static String treeToString(@NotNull ASTNode root, boolean skipWhitespaces, boolean showRanges) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, skipWhitespaces, showRanges, false, true);
    return buffer.toString();
  }

  public static void treeToBuffer(@NotNull final Appendable buffer,
                                  @NotNull final ASTNode root,
                                  final int indent,
                                  final boolean skipWhiteSpaces,
                                  final boolean showRanges,
                                  final boolean showChildrenRanges,
                                  final boolean usePsi) {
    treeToBuffer(buffer, root, indent, skipWhiteSpaces, showRanges, showChildrenRanges, usePsi, null);
  }

  public static void treeToBuffer(@NotNull final Appendable buffer,
                                  @NotNull final ASTNode root,
                                  final int indent,
                                  final boolean skipWhiteSpaces,
                                  final boolean showRanges,
                                  final boolean showChildrenRanges,
                                  final boolean usePsi,
                                  @Nullable PairConsumer<PsiElement, Consumer<PsiElement>> extra) {
    ((TreeElement) root).acceptTree(
      new TreeToBuffer(buffer, indent, skipWhiteSpaces, showRanges, showChildrenRanges, usePsi, extra));
  }

  private static class TreeToBuffer extends RecursiveTreeElementWalkingVisitor {
    final Appendable buffer;
    final boolean skipWhiteSpaces;
    final boolean showRanges;
    final boolean showChildrenRanges;
    final boolean usePsi;
    final PairConsumer<PsiElement, Consumer<PsiElement>> extra;
    int indent;

    TreeToBuffer(Appendable buffer, int indent, boolean skipWhiteSpaces,
                 boolean showRanges, boolean showChildrenRanges, boolean usePsi,
                 PairConsumer<PsiElement, Consumer<PsiElement>> extra) {
      this.buffer = buffer;
      this.skipWhiteSpaces = skipWhiteSpaces;
      this.showRanges = showRanges;
      this.showChildrenRanges = showChildrenRanges;
      this.usePsi = usePsi;
      this.extra = extra;
      this.indent = indent;
    }

    @Override
    protected void visitNode(TreeElement root) {
      if (shouldSkipNode(root)) {
        indent += 2;
        return;
      }

      StringUtil.repeatSymbol(buffer, ' ', indent);
      try {
        if (root instanceof CompositeElement) {
          if (usePsi) {
            PsiElement psiElement = root.getPsi();
            if (psiElement != null) {
              buffer.append(psiElement.toString());
            }
            else {
              buffer.append(root.getElementType().toString());
            }
          }
          else {
            buffer.append(root.toString());
          }
        }
        else {
          final String text = fixWhiteSpaces(root.getText());
          buffer.append(root.toString()).append("('").append(text).append("')");
        }
        if (showRanges) buffer.append(root.getTextRange().toString());
        buffer.append("\n");
        indent += 2;
        if (root instanceof CompositeElement && root.getFirstChildNode() == null) {
          StringUtil.repeatSymbol(buffer, ' ', indent);
          buffer.append("<empty list>\n");
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }

      super.visitNode(root);
    }

    protected boolean shouldSkipNode(TreeElement node) {
      return skipWhiteSpaces && node.getElementType() == TokenType.WHITE_SPACE;
    }

    @Override
    protected void elementFinished(@NotNull ASTNode e) {
      PsiElement psiElement = extra != null && usePsi && e instanceof CompositeElement ? e.getPsi() : null;
      if (psiElement != null) {
        extra.consume(psiElement, element ->
          treeToBuffer(buffer, element.getNode(), indent, skipWhiteSpaces, showRanges, showChildrenRanges, true, null));
      }
      indent -= 2;
    }
  }

  public static String lightTreeToString(@NotNull final FlyweightCapableTreeStructure<LighterASTNode> tree,
                                         final boolean skipWhitespaces) {
    final StringBuilder buffer = new StringBuilder();
    lightTreeToBuffer(tree, tree.getRoot(), buffer, 0, skipWhitespaces);
    return buffer.toString();
  }

  public static void lightTreeToBuffer(@NotNull final FlyweightCapableTreeStructure<LighterASTNode> tree,
                                       @NotNull final LighterASTNode node,
                                       @NotNull final Appendable buffer,
                                       final int indent,
                                       final boolean skipWhiteSpaces) {
    final IElementType tokenType = node.getTokenType();
    if (skipWhiteSpaces && tokenType == TokenType.WHITE_SPACE) return;

    final boolean isLeaf = (node instanceof LighterASTTokenNode);

    StringUtil.repeatSymbol(buffer, ' ', indent);
    try {
      if (tokenType == TokenType.ERROR_ELEMENT) {
        buffer.append("PsiErrorElement:").append(PsiBuilderImpl.getErrorMessage(node));
      }
      else if (tokenType == TokenType.WHITE_SPACE) {
        buffer.append("PsiWhiteSpace");
      }
      else {
        buffer.append(isLeaf ? "PsiElement" : "Element").append('(').append(tokenType.toString()).append(')');
      }

      if (isLeaf) {
        final String text = ((LighterASTTokenNode)node).getText().toString();
        buffer.append("('").append(fixWhiteSpaces(text)).append("')");
      }
      buffer.append('\n');

      if (!isLeaf) {
        final Ref<LighterASTNode[]> kids = new Ref<>();
        final int numKids = tree.getChildren(node, kids);
        if (numKids == 0) {
          StringUtil.repeatSymbol(buffer, ' ', indent + 2);
          buffer.append("<empty list>\n");
        }
        else {
          for (int i = 0; i < numKids; i++) {
            lightTreeToBuffer(tree, kids.get()[i], buffer, indent + 2, skipWhiteSpaces);
          }
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static String stubTreeToString(final Stub root) {
    StringBuilder builder = new StringBuilder();
    stubTreeToBuffer(root, builder, 0);
    return builder.toString();
  }

  public static void stubTreeToBuffer(final Stub node, final Appendable buffer, final int indent) {
    StringUtil.repeatSymbol(buffer, ' ', indent);
    try {
      final ObjectStubSerializer stubType = node.getStubType();
      if (stubType != null) {
        buffer.append(stubType.toString()).append(':');
      }
      buffer.append(node.toString()).append('\n');

      @SuppressWarnings({"unchecked"})
      final List<? extends Stub> children = node.getChildrenStubs();
      for (final Stub child : children) {
        stubTreeToBuffer(child, buffer, indent + 2);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void treeToBufferWithUserData(Appendable buffer, TreeElement root, int indent, boolean skipWhiteSpaces) {
    if (skipWhiteSpaces && root.getElementType() == TokenType.WHITE_SPACE) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    try {
      final PsiElement psi = SourceTreeToPsiMap.treeElementToPsi(root);
      assert psi != null : root;
      if (root instanceof CompositeElement) {
        buffer.append(psi.toString());
      }
      else {
        final String text = fixWhiteSpaces(root.getText());
        buffer.append(root.toString()).append("('").append(text).append("')");
      }
      buffer.append(root.getUserDataString());
      buffer.append("\n");
      if (root instanceof CompositeElement) {
        PsiElement[] children = psi.getChildren();

        for (PsiElement child : children) {
          treeToBufferWithUserData(buffer, (TreeElement)SourceTreeToPsiMap.psiElementToTree(child), indent + 2, skipWhiteSpaces);
        }

        if (children.length == 0) {
          StringUtil.repeatSymbol(buffer, ' ', indent + 2);
          buffer.append("<empty list>\n");
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void treeToBufferWithUserData(Appendable buffer, PsiElement root, int indent, boolean skipWhiteSpaces) {
    if (skipWhiteSpaces && root instanceof PsiWhiteSpace) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    try {
      if (root instanceof CompositeElement) {
        buffer.append(root.toString());
      }
      else {
        final String text = fixWhiteSpaces(root.getText());
        buffer.append(root.toString()).append("('").append(text).append("')");
      }
      buffer.append(((UserDataHolderBase)root).getUserDataString());
      buffer.append("\n");

      PsiElement[] children = root.getChildren();

      for (PsiElement child : children) {
        treeToBufferWithUserData(buffer, child, indent + 2, skipWhiteSpaces);
      }

      if (children.length == 0) {
        StringUtil.repeatSymbol(buffer, ' ', indent + 2);
        buffer.append("<empty list>\n");
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static void doCheckTreeStructure(@Nullable ASTNode anyElement) {
    if (anyElement == null) return;
    ASTNode root = anyElement;
    while (root.getTreeParent() != null) {
      root = root.getTreeParent();
    }
    if (root instanceof CompositeElement) {
      checkSubtree((CompositeElement)root);
    }
  }

  private static void checkSubtree(CompositeElement root) {
    if (root.rawFirstChild() == null) {
      if (root.rawLastChild() != null) {
        throw new IncorrectTreeStructureException(root, "firstChild == null, but lastChild != null");
      }
    }
    else {
      for (ASTNode child = root.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        if (child instanceof CompositeElement) {
          checkSubtree((CompositeElement)child);
        }
        if (child.getTreeParent() != root) {
          throw new IncorrectTreeStructureException(child, "child has wrong parent value");
        }
        if (child == root.getFirstChildNode()) {
          if (child.getTreePrev() != null) {
            throw new IncorrectTreeStructureException(root, "firstChild.prev != null");
          }
        }
        else {
          if (child.getTreePrev() == null) {
            throw new IncorrectTreeStructureException(child, "not first child has prev == null");
          }
          if (child.getTreePrev().getTreeNext() != child) {
            throw new IncorrectTreeStructureException(child, "element.prev.next != element");
          }
        }
        if (child.getTreeNext() == null) {
          if (root.getLastChildNode() != child) {
            throw new IncorrectTreeStructureException(child, "not last child has next == null");
          }
        }
      }
    }
  }

  public static void checkParentChildConsistent(@NotNull ASTNode element) {
    ASTNode treeParent = element.getTreeParent();
    if (treeParent == null) return;
    ASTNode[] elements = treeParent.getChildren(null);
    if (ArrayUtil.find(elements, element) == -1) {
      throw new IncorrectTreeStructureException(element, "child cannot be found among parents children");
    }
    //LOG.debug("checked consistence: "+System.identityHashCode(element));
  }

  public static void checkSameCharTabs(@NotNull ASTNode element1, @NotNull ASTNode element2) {
    final CharTable fromCharTab = SharedImplUtil.findCharTableByTree(element1);
    final CharTable toCharTab = SharedImplUtil.findCharTableByTree(element2);
    LOG.assertTrue(fromCharTab == toCharTab);
  }

  public static String psiToString(@NotNull PsiElement element, final boolean skipWhitespaces) {
    return psiToString(element, skipWhitespaces, false);
  }

  public static String psiToString(@NotNull final PsiElement root, final boolean skipWhiteSpaces, final boolean showRanges) {
    return psiToString(root, skipWhiteSpaces, showRanges, null);
  }

  public static String psiToString(@NotNull final PsiElement root, final boolean skipWhiteSpaces, final boolean showRanges, PairConsumer<PsiElement, Consumer<PsiElement>> extra) {
    StringBuilder buffer = new StringBuilder();
    psiToBuffer(buffer, root, skipWhiteSpaces, showRanges, extra);
    return buffer.toString();
  }

  @NotNull
  public static String psiToStringIgnoringNonCode(@NotNull PsiElement element) {
    StringBuilder buffer = new StringBuilder();
    ((TreeElement)element.getNode()).acceptTree(
      new TreeToBuffer(buffer, 0, true, false, false, true, null) {
        @Override
        protected boolean shouldSkipNode(TreeElement node) {
          return super.shouldSkipNode(node) || node instanceof PsiErrorElement || node instanceof PsiComment;
        }
      });
    return buffer.toString();
  }

  private static void psiToBuffer(final Appendable buffer,
                                  final PsiElement root,
                                  final boolean skipWhiteSpaces,
                                  final boolean showRanges,
                                  PairConsumer<PsiElement, Consumer<PsiElement>> extra) {
    final ASTNode node = root.getNode();
    if (node == null) {
      psiToBuffer(buffer, root, 0, skipWhiteSpaces, showRanges, showRanges, extra);
    }
    else {
      treeToBuffer(buffer, node, 0, skipWhiteSpaces, showRanges, showRanges, true, extra);
    }
  }

  public static void psiToBuffer(@NotNull final Appendable buffer,
                                 @NotNull final PsiElement root,
                                 int indent,
                                 boolean skipWhiteSpaces,
                                 boolean showRanges,
                                 boolean showChildrenRanges) {
    psiToBuffer(buffer, root, indent, skipWhiteSpaces, showRanges, showChildrenRanges, null);
  }

  public static void psiToBuffer(@NotNull final Appendable buffer,
                                 @NotNull final PsiElement root,
                                 final int indent,
                                 final boolean skipWhiteSpaces,
                                 boolean showRanges,
                                 final boolean showChildrenRanges,
                                 PairConsumer<PsiElement, Consumer<PsiElement>> extra) {
    if (skipWhiteSpaces && root instanceof PsiWhiteSpace) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    try {
      buffer.append(root.toString());
      PsiElement child = root.getFirstChild();
      if (child == null) {
        final String text = root.getText();
        assert text != null : "text is null for <" + root + ">";
        buffer.append("('").append(fixWhiteSpaces(text)).append("')");
      }

      if (showRanges) buffer.append(root.getTextRange().toString());
      buffer.append("\n");
      while (child != null) {
        psiToBuffer(buffer, child, indent + 2, skipWhiteSpaces, showChildrenRanges, showChildrenRanges, extra);
        child = child.getNextSibling();
      }
      if (extra != null) {
        extra.consume(root,
                      element -> psiToBuffer(buffer, element, indent + 2, skipWhiteSpaces, showChildrenRanges, showChildrenRanges, null));
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static String fixWhiteSpaces(String text) {
    text = StringUtil.replace(text, "\n", "\\n");
    text = StringUtil.replace(text, "\r", "\\r");
    text = StringUtil.replace(text, "\t", "\\t");
    return text;
  }

  public static String currentStackTrace() {
    return ExceptionUtil.currentStackTrace();
  }

  public static class IncorrectTreeStructureException extends RuntimeException {
    private final ASTNode myElement;

    public IncorrectTreeStructureException(ASTNode element, String message) {
      super(message);
      myElement = element;
    }

    public ASTNode getElement() {
      return myElement;
    }
  }

  private static final ThreadLocal<Object> ourPsiModificationTrace = new ThreadLocal<>();
  private static final ThreadLocal<Integer> ourPsiModificationDepth = new ThreadLocal<>();
  private static final Set<Integer> ourNonTransactedTraces = ContainerUtil.newConcurrentSet();

  /**
   * Marks a start of PSI modification action. Any PSI/AST elements invalidated inside such an action will contain a debug trace
   * identifying this transaction, and so will {@link PsiInvalidElementAccessException} thrown when accessing such invalid
   * elements. This should help finding out why a specific PSI element has become invalid.
   *
   * @param trace The debug trace that the invalidated elements should be identified by. May be null, then current stack trace is used.
   */
  public static void startPsiModification(@Nullable String trace) {
    if (!PsiInvalidElementAccessException.isTrackingInvalidation()) {
      return;
    }

    //noinspection ThrowableResultOfMethodCallIgnored
    if (ourPsiModificationTrace.get() == null) {
      ourPsiModificationTrace.set(trace != null ? trace : new Throwable());
    }
    Integer depth = ourPsiModificationDepth.get();
    if (depth == null) depth = 0;
    ourPsiModificationDepth.set(depth + 1);
  }

  /**
   * Finished PSI modification action.
   * @see #startPsiModification(String)
   */
  public static void finishPsiModification() {
    if (!PsiInvalidElementAccessException.isTrackingInvalidation()) {
      return;
    }
    Integer depth = ourPsiModificationDepth.get();
    if (depth == null) {
      LOG.warn("Unmatched PSI modification end", new Throwable());
      depth = 0;
    } else {
      depth--;
      ourPsiModificationDepth.set(depth);
    }
    if (depth == 0) {
      ourPsiModificationTrace.set(null);
    }
  }

  public static void onInvalidated(@NotNull ASTNode treeElement) {
    Object trace = calcInvalidationTrace(treeElement);
    if (trace != null) {
      PsiInvalidElementAccessException.setInvalidationTrace(treeElement, trace);
    }
  }

  public static void onInvalidated(@NotNull PsiElement o) {
    Object trace = PsiInvalidElementAccessException.getInvalidationTrace(o);
    if (trace != null) return;

    PsiInvalidElementAccessException.setInvalidationTrace(o, currentInvalidationTrace());
  }

  public static void onInvalidated(@NotNull FileViewProvider provider) {
    Object trace = calcInvalidationTrace(null);
    if (trace != null) {
      PsiInvalidElementAccessException.setInvalidationTrace(provider, trace);
    }
  }

  @Nullable
  private static Object calcInvalidationTrace(@Nullable ASTNode treeElement) {
    if (!PsiInvalidElementAccessException.isTrackingInvalidation()) {
      return null;
    }
    if (PsiInvalidElementAccessException.findInvalidationTrace(treeElement) != null) {
      return null;
    }

    return currentInvalidationTrace();
  }

  @NotNull
  private static Object currentInvalidationTrace() {
    Object trace = ourPsiModificationTrace.get();
    if (trace == null) {
      trace = new Throwable();
      if (ourNonTransactedTraces.add(ExceptionUtil.getThrowableText((Throwable)trace).hashCode())) {
        LOG.info("PSI invalidated outside transaction", (Throwable)trace);
      }
    }
    return trace;
  }

  public static void revalidateNode(@NotNull ASTNode element) {
    PsiInvalidElementAccessException.setInvalidationTrace(element, null);
  }

  public static void sleep(long millis) {
    TimeoutUtil.sleep(millis);
  }
  public static void checkTreeStructure(ASTNode element) {
    if (CHECK){
      doCheckTreeStructure(element);
    }
  }

  @NotNull
  public static String diagnosePsiDocumentInconsistency(@NotNull PsiElement element, @NotNull Document document) {
    PsiUtilCore.ensureValid(element);

    PsiFile file = element.getContainingFile();
    if (file == null) return "no file for " + element + " of " + element.getClass();

    PsiUtilCore.ensureValid(file);

    FileViewProvider viewProvider = file.getViewProvider();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());

    Document actualDocument = manager.getDocument(file);
    String fileDiagnostics = "File[" + file + " " + file.getName() + ", " + file.getLanguage() + ", " + viewProvider + "]";
    if (actualDocument != document) {
      return "wrong document for " + fileDiagnostics + "; expected " + document + "; actual " + actualDocument;
    }

    PsiFile cachedPsiFile = manager.getCachedPsiFile(document);
    FileViewProvider actualViewProvider = cachedPsiFile == null ? null : cachedPsiFile.getViewProvider();
    if (actualViewProvider != viewProvider) {
      return "wrong view provider for " + document + ", expected " + viewProvider + "; actual " + actualViewProvider;
    }

    if (!manager.isCommitted(document)) return "not committed document " + document + ", " + fileDiagnostics;

    int fileLength = file.getTextLength();
    int docLength = document.getTextLength();
    if (fileLength != docLength) {
      return "file/doc text length different, " + fileDiagnostics + " file.length=" + fileLength + "; doc.length=" + docLength;
    }

    return "unknown inconsistency in " + fileDiagnostics;
  }

  public static <T> String graphToString(InboundSemiGraph<T> graph) {
    StringBuilder buffer = new StringBuilder();
    printNodes(graph.getNodes().iterator(), node -> graph.getIn(node), 0, new HashSet<>(), buffer);
    return buffer.toString();
  }

  public static <T> String graphToString(OutboundSemiGraph<T> graph) {
    StringBuilder buffer = new StringBuilder();
    printNodes(graph.getNodes().iterator(), node -> graph.getOut(node), 0, new HashSet<>(), buffer);
    return buffer.toString();
  }

  private static <T> void printNodes(Iterator<T> nodes, Function<T, Iterator<T>> getter, int indent, Set<T> visited, StringBuilder buffer) {
    while (nodes.hasNext()) {
      T node = nodes.next();
      StringUtil.repeatSymbol(buffer, ' ', indent);
      buffer.append(node);
      if (visited.add(node)) {
        buffer.append('\n');
        printNodes(getter.fun(node), getter, indent + 2, visited, buffer);
      }
      else {
        buffer.append(" [...]\n");
      }
    }
  }
}