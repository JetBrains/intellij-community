// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.graph.InboundSemiGraph;
import com.intellij.util.graph.OutboundSemiGraph;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class DebugUtil {
  private static final Logger LOG = Logger.getInstance(DebugUtil.class);
  @SuppressWarnings("StaticNonFinalField") private static boolean CHECK;
  public static final boolean DO_EXPENSIVE_CHECKS;

  static {
    Application application = ApplicationManager.getApplication();
    DO_EXPENSIVE_CHECKS = application != null && application.isUnitTestMode();
  }

  public static final boolean CHECK_INSIDE_ATOMIC_ACTION_ENABLED = DO_EXPENSIVE_CHECKS;

  public static @NotNull String psiTreeToString(@NotNull PsiElement element, boolean showWhitespaces) {
    ASTNode node = SourceTreeToPsiMap.psiElementToTree(element);
    assert node != null : element;
    return treeToString(node, showWhitespaces);
  }

  public static @NotNull String treeToString(@NotNull ASTNode root, boolean showWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, showWhitespaces, false, false, false, true);
    return buffer.toString();
  }

  public static @NotNull String nodeTreeToString(@NotNull ASTNode root, boolean showWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, showWhitespaces, false, false, false, false);
    return buffer.toString();
  }

  public static @NotNull String nodeTreeAsElementTypeToString(@NotNull ASTNode root, boolean showWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, showWhitespaces, false, false, false, false, true, null);
    return buffer.toString();
  }

  public static @NotNull String treeToString(@NotNull ASTNode root, boolean showWhitespaces, boolean showRanges) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, showWhitespaces, showRanges, false, false, true);
    return buffer.toString();
  }

  public static void treeToBuffer(@NotNull Appendable buffer,
                                  @NotNull ASTNode root,
                                  int indent,
                                  boolean showWhitespaces,
                                  boolean showRanges,
                                  boolean showChildrenRanges,
                                  boolean showClassNames,
                                  boolean usePsi) {
    treeToBuffer(buffer, root, indent, showWhitespaces, showRanges, showChildrenRanges, showClassNames, usePsi, null);
  }

  private static void treeToBuffer(Appendable buffer,
                                   ASTNode root,
                                   int indent,
                                   boolean showWhitespaces,
                                   boolean showRanges,
                                   boolean showChildrenRanges,
                                   boolean showClassNames,
                                   boolean usePsi,
                                   @Nullable PairConsumer<? super PsiElement, ? super Consumer<? super PsiElement>> extra) {
    treeToBuffer(buffer, root, indent, showWhitespaces, showRanges, showChildrenRanges, showClassNames, usePsi, false, extra);
  }

  private static void treeToBuffer(Appendable buffer,
                                   ASTNode root,
                                   int indent,
                                   boolean showWhitespaces,
                                   boolean showRanges,
                                   boolean showChildrenRanges,
                                   boolean showClassNames,
                                   boolean usePsi,
                                   boolean useElementType,
                                   @Nullable PairConsumer<? super PsiElement, ? super Consumer<? super PsiElement>> extra) {
    ((TreeElement)root).acceptTree(
      new TreeToBuffer(buffer, indent, showWhitespaces, showRanges, showChildrenRanges, showClassNames, usePsi, useElementType, extra));
  }

  private static class TreeToBuffer extends RecursiveTreeElementWalkingVisitor {
    private final Appendable buffer;
    private final boolean showWhitespaces;
    private final boolean showRanges;
    private final boolean showClassNames;
    private final boolean showChildrenRanges;
    private final boolean usePsi;
    private final boolean useElementType;
    private final PairConsumer<? super PsiElement, ? super Consumer<? super PsiElement>> extra;
    private int indent;

    private TreeToBuffer(Appendable buffer,
                         int indent,
                         boolean showWhitespaces,
                         boolean showRanges,
                         boolean showChildrenRanges,
                         boolean showClassNames,
                         boolean usePsi,
                         boolean useElementType,
                         PairConsumer<? super PsiElement, ? super Consumer<? super PsiElement>> extra) {
      this.buffer = buffer;
      this.showWhitespaces = showWhitespaces;
      this.showRanges = showRanges;
      this.showChildrenRanges = showChildrenRanges;
      this.showClassNames = showClassNames;
      this.usePsi = usePsi;
      this.useElementType = useElementType;
      this.extra = extra;
      this.indent = indent;
    }

    @Override
    protected void visitNode(TreeElement root) {
      if (!shouldShowNode(root)) {
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
            if (useElementType) {
              buffer.append(root.getElementType().toString());
            }
            else {
              buffer.append(root.toString());
            }
          }
        }
        else {
          if (useElementType) {
            buffer.append(root.getElementType().toString());
          }
          else {
            String text = fixWhiteSpaces(root.getText());
            buffer.append(root.toString()).append("('").append(text).append("')");
          }
        }
        if (showRanges) buffer.append(root.getTextRange().toString());
        if (showClassNames) buffer.append("[").append(getPsiClassName(root.getPsi())).append("]");
        buffer.append("\n");
        indent += 2;
        if (root instanceof CompositeElement && root.getFirstChildNode() == null && showEmptyChildren()) {
          StringUtil.repeatSymbol(buffer, ' ', indent);
          buffer.append("<empty list>\n");
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }

      super.visitNode(root);
    }

    protected boolean showEmptyChildren() {
      return true;
    }

    protected boolean shouldShowNode(TreeElement node) {
      return showWhitespaces || node.getElementType() != TokenType.WHITE_SPACE;
    }

    @Override
    protected void elementFinished(@NotNull ASTNode e) {
      PsiElement psiElement = extra != null && usePsi && e instanceof CompositeElement ? e.getPsi() : null;
      if (psiElement != null) {
        Consumer<PsiElement> consumer = element ->
          treeToBuffer(buffer, element.getNode(), indent, showWhitespaces, showRanges, showChildrenRanges, showClassNames, true, null);
        extra.consume(psiElement, consumer);
      }
      indent -= 2;
    }
  }

  public static @NotNull String lightTreeToString(@NotNull FlyweightCapableTreeStructure<LighterASTNode> tree, boolean showWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    lightTreeToBuffer(tree, tree.getRoot(), buffer, 0, showWhitespaces);
    return buffer.toString();
  }

  private static void lightTreeToBuffer(FlyweightCapableTreeStructure<LighterASTNode> tree,
                                        @NotNull LighterASTNode node,
                                        Appendable buffer,
                                        int indent,
                                        boolean showWhitespaces) {
    IElementType tokenType = node.getTokenType();
    if (!showWhitespaces && tokenType == TokenType.WHITE_SPACE) return;

    boolean isLeaf = node instanceof LighterASTTokenNode;

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
        String text = ((LighterASTTokenNode)node).getText().toString();
        buffer.append("('").append(fixWhiteSpaces(text)).append("')");
      }
      buffer.append('\n');

      if (!isLeaf) {
        Ref<LighterASTNode[]> kids = new Ref<>();
        int numKids = tree.getChildren(node, kids);
        if (numKids == 0) {
          StringUtil.repeatSymbol(buffer, ' ', indent + 2);
          buffer.append("<empty list>\n");
        }
        else {
          for (int i = 0; i < numKids; i++) {
            lightTreeToBuffer(tree, kids.get()[i], buffer, indent + 2, showWhitespaces);
          }
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static @NotNull String stubTreeToString(@NotNull Stub root) {
    StringBuilder builder = new StringBuilder();
    stubTreeToBuffer(root, builder, 0);
    return builder.toString();
  }

  public static void stubTreeToBuffer(@NotNull Stub node, @NotNull Appendable buffer, int indent) {
    StringUtil.repeatSymbol(buffer, ' ', indent);
    try {
      ObjectStubSerializer<?, ?> stubType = node.getStubType();
      if (stubType != null) {
        buffer.append(stubType.toString()).append(':');
      }
      buffer.append(node.toString()).append('\n');

      List<? extends Stub> children = node.getChildrenStubs();
      for (Stub child : children) {
        stubTreeToBuffer(child, buffer, indent + 2);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void doCheckTreeStructure(@Nullable ASTNode anyElement) {
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
  }

  public static void checkSameCharTabs(@NotNull ASTNode element1, @NotNull ASTNode element2) {
    CharTable fromCharTab = SharedImplUtil.findCharTableByTree(element1);
    CharTable toCharTab = SharedImplUtil.findCharTableByTree(element2);
    LOG.assertTrue(fromCharTab == toCharTab);
  }

  public static @NotNull String psiToString(@NotNull PsiElement element, boolean showWhitespaces) {
    return psiToString(element, showWhitespaces, false);
  }

  public static @NotNull String psiToString(@NotNull PsiElement root, boolean showWhitespaces, boolean showRanges) {
    return psiToString(root, showWhitespaces, showRanges, null);
  }

  public static @NotNull String psiToString(@NotNull PsiElement root,
                                            boolean showWhitespaces,
                                            boolean showRanges,
                                            @Nullable PairConsumer<? super PsiElement, ? super Consumer<? super PsiElement>> extra) {
    return psiToString(root, showWhitespaces, showRanges, false, extra);
  }

  public static @NotNull String psiToString(@NotNull PsiElement root,
                                            boolean showWhitespaces,
                                            boolean showRanges,
                                            boolean showClassNames,
                                            @Nullable PairConsumer<? super PsiElement, ? super Consumer<? super PsiElement>> extra) {
    StringBuilder buffer = new StringBuilder();
    psiToBuffer(buffer, root, showWhitespaces, showRanges, showClassNames, extra);
    return buffer.toString();
  }

  public static @NotNull String psiToStringIgnoringNonCode(@NotNull PsiElement element) {
    StringBuilder buffer = new StringBuilder();
    ((TreeElement)element.getNode()).acceptTree(
      new TreeToBuffer(buffer, 0, false, false, false, false, false, false,null) {
        @Override
        protected boolean shouldShowNode(TreeElement node) {
          return super.shouldShowNode(node) &&
                 !(node instanceof PsiErrorElement) && !(node instanceof PsiComment) &&
                 !(node instanceof LeafPsiElement && StringUtil.isEmptyOrSpaces(node.getText())) &&
                 !(node instanceof OuterLanguageElement);
        }

        @Override
        protected boolean showEmptyChildren() {
          return false;
        }
      });
    return buffer.toString();
  }

  private static void psiToBuffer(Appendable buffer,
                                  PsiElement root,
                                  boolean showWhitespaces,
                                  boolean showRanges,
                                  boolean showClassNames,
                                  @Nullable PairConsumer<? super PsiElement, ? super Consumer<? super PsiElement>> extra) {
    ASTNode node = root.getNode();
    if (node == null) {
      psiToBuffer(buffer, root, 0, showWhitespaces, showRanges, showRanges, showClassNames, extra);
    }
    else {
      treeToBuffer(buffer, node, 0, showWhitespaces, showRanges, showRanges, showClassNames, true, extra);
    }
  }

  public static void psiToBuffer(@NotNull Appendable buffer,
                                 @NotNull PsiElement root,
                                 int indent,
                                 boolean showWhitespaces,
                                 boolean showRanges,
                                 boolean showChildrenRanges,
                                 boolean showClassNames) {
    psiToBuffer(buffer, root, indent, showWhitespaces, showRanges, showChildrenRanges, showClassNames, null);
  }

  private static void psiToBuffer(Appendable buffer,
                                  PsiElement root,
                                  int indent,
                                  boolean showWhitespaces,
                                  boolean showRanges,
                                  boolean showChildrenRanges,
                                  boolean showClassNames,
                                  @Nullable PairConsumer<? super PsiElement, ? super Consumer<? super PsiElement>> extra) {
    if (!showWhitespaces && root instanceof PsiWhiteSpace) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    try {
      buffer.append(root.toString());
      PsiElement child = root.getFirstChild();
      if (child == null) {
        String text = root.getText();
        assert text != null : "text is null for <" + root + ">";
        buffer.append("('").append(fixWhiteSpaces(text)).append("')");
      }

      if (showRanges) buffer.append(root.getTextRange().toString());
      if (showClassNames) buffer.append("[").append(getPsiClassName(root)).append("]");
      buffer.append("\n");
      while (child != null) {
        psiToBuffer(buffer, child, indent + 2, showWhitespaces, showChildrenRanges, showChildrenRanges, showClassNames, extra);
        child = child.getNextSibling();
      }
      if (extra != null) {
        Consumer<PsiElement> consumer =
          element -> psiToBuffer(buffer, element, indent + 2, !showWhitespaces, showChildrenRanges, showChildrenRanges, showClassNames,
                                 null);
        extra.consume(root, consumer);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static String fixWhiteSpaces(String text) {
    return text.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  public static @NotNull String currentStackTrace() {
    return ExceptionUtil.currentStackTrace();
  }

  public static class IncorrectTreeStructureException extends RuntimeException {
    private final ASTNode myElement;

    IncorrectTreeStructureException(ASTNode element, String message) {
      super(message);
      myElement = element;
    }

    public ASTNode getElement() {
      return myElement;
    }
  }

  private static final ThreadLocal<Object> ourPsiModificationTrace = new ThreadLocal<>();
  private static final ThreadLocal<Integer> ourPsiModificationDepth = new ThreadLocal<>();

  private static void beginPsiModification(@Nullable String trace) {
    if (!PsiInvalidElementAccessException.isTrackingInvalidation()) {
      return;
    }
    if (ourPsiModificationTrace.get() == null) {
      ourPsiModificationTrace.set(trace != null || ApplicationManagerEx.isInStressTest() ? trace : new Throwable());
    }
    Integer depth = ourPsiModificationDepth.get();
    if (depth == null) depth = 0;
    ourPsiModificationDepth.set(depth + 1);
  }

  private static void endPsiModification() {
    if (!PsiInvalidElementAccessException.isTrackingInvalidation()) {
      return;
    }
    Integer depth = ourPsiModificationDepth.get();
    if (depth == null) {
      LOG.warn("Unmatched PSI modification end", new Throwable());
      depth = 0;
    }
    else {
      depth--;
      ourPsiModificationDepth.set(depth);
    }
    if (depth == 0) {
      ourPsiModificationTrace.set(null);
    }
  }

  /**
   * Any PSI/AST elements invalidated inside the given action will contain a debug trace identifying this transaction,
   * and so will {@link PsiInvalidElementAccessException} thrown when accessing such invalid elements.
   * This should help find out why a specific PSI element has become invalid.
   *
   * @param trace The debug trace that the invalidated elements should be identified by. May be null, then current stack trace is used.
   */
  public static <T extends Throwable> void performPsiModification(@Nullable String trace, @NotNull ThrowableRunnable<T> runnable) throws T {
    beginPsiModification(trace);
    try {
      runnable.run();
    }
    finally {
      endPsiModification();
    }
  }

  /**
   * @see #performPsiModification(String, ThrowableRunnable)
   */
  public static <T, E extends Throwable> T performPsiModification(@Nullable String trace, @NotNull ThrowableComputable<T, E> runnable) throws E {
    beginPsiModification(trace);
    try {
      return runnable.compute();
    }
    finally {
      endPsiModification();
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
    if (trace == null) {
      PsiInvalidElementAccessException.setInvalidationTrace(o, currentInvalidationTrace());
    }
  }

  public static void onInvalidated(@NotNull FileViewProvider provider) {
    Object trace = calcInvalidationTrace(null);
    if (trace != null) {
      PsiInvalidElementAccessException.setInvalidationTrace(provider, trace);
    }
  }

  private static @Nullable Object calcInvalidationTrace(@Nullable ASTNode treeElement) {
    if (!PsiInvalidElementAccessException.isTrackingInvalidation()) {
      return null;
    }
    if (PsiInvalidElementAccessException.findInvalidationTrace(treeElement) != null) {
      return null;
    }

    return currentInvalidationTrace();
  }

  private static @Nullable Object currentInvalidationTrace() {
    Object trace = ourPsiModificationTrace.get();
    return trace != null || ApplicationManagerEx.isInStressTest() ? trace : handleUnspecifiedTrace();
  }

  private static Throwable handleUnspecifiedTrace() {
    Throwable trace = new Throwable();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error("PSI invalidated outside transaction", trace);
    }
    else {
      LOG.info("PSI invalidated outside transaction", trace);
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
    if (CHECK) {
      doCheckTreeStructure(element);
    }
  }

  public static @NotNull String diagnosePsiDocumentInconsistency(@NotNull PsiElement element, @NotNull Document document) {
    PsiUtilCore.ensureValid(element);

    PsiFile file = element.getContainingFile();
    if (file == null) return "no file for " + element + " of " + element.getClass();

    PsiUtilCore.ensureValid(file);

    FileViewProvider viewProvider = file.getViewProvider();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());

    Document actualDocument = viewProvider.getDocument();
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

  public static @NotNull <T> String graphToString(@NotNull InboundSemiGraph<T> graph) {
    StringBuilder buffer = new StringBuilder();
    printNodes(graph.getNodes().iterator(), node -> graph.getIn(node), 0, new HashSet<>(), buffer);
    return buffer.toString();
  }

  public static @NotNull <T> String graphToString(@NotNull OutboundSemiGraph<T> graph) {
    StringBuilder buffer = new StringBuilder();
    printNodes(graph.getNodes().iterator(), node -> graph.getOut(node), 0, new HashSet<>(), buffer);
    return buffer.toString();
  }

  private static <T> void printNodes(Iterator<? extends T> nodes,
                                     Function<? super T, ? extends Iterator<? extends T>> getter,
                                     int indent,
                                     Set<? super T> visited,
                                     StringBuilder buffer) {
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

  private static @NotNull String getPsiClassName(@NotNull PsiElement psi) {
    String name = psi.getClass().getCanonicalName();
    return name.replace("com.", "c.")
      .replace("org.", "o.")
      .replace(".intellij.", ".i.")
      .replace(".jetbrains.", ".j.")
      .replace(".psi.", ".p.")
      .replace(".impl.", ".i.")
      .replace(".source.", ".s.")
      .replace(".lang.", ".l.");
  }

  @TestOnly
  public static void runWithCheckInternalInvariantsEnabled(@NotNull ThrowableRunnable<?> runnable) throws Throwable {
    boolean oldDebugUtilCheck = DebugUtil.CHECK;
    DebugUtil.CHECK = true;
    try {
      runnable.run();
    }
    finally {
      DebugUtil.CHECK = oldDebugUtilCheck;
    }
  }
  @TestOnly
  public static void runWithCheckInternalInvariantsDisabled(@NotNull ThrowableRunnable<?> runnable) throws Throwable {
    boolean oldDebugUtilCheck = DebugUtil.CHECK;
    DebugUtil.CHECK = false;
    try {
      runnable.run();
    }
    finally {
      DebugUtil.CHECK = oldDebugUtilCheck;
    }
  }

  //<editor-fold desc="Deprecated stuff">

  /** @deprecated use {@link #performPsiModification} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void startPsiModification(@Nullable String trace) {
    beginPsiModification(trace);
  }

  /** @deprecated use {@link #performPsiModification} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void finishPsiModification() {
    endPsiModification();
  }
  //</editor-fold>
}
