/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.*;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor", "UnusedDeclaration", "TestOnlyProblems"})
public class DebugUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DebugUtil");

  public static class LengthBuilder implements Appendable {
    private int myLength = 0;

    public int getLength() {
      return myLength;
    }

    @Override
    public Appendable append(final CharSequence csq) {
      myLength += csq.length();
      return this;
    }

    @Override
    public Appendable append(final CharSequence csq, final int start, final int end) {
      myLength += csq.subSequence(start, end).length();
      return this;
    }

    @Override
    public Appendable append(final char c) {
      myLength++;
      return this;
    }
  }

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
    final LengthBuilder ruler = new LengthBuilder();
    treeToBuffer(ruler, root, 0, skipWhitespaces, false, false, true);
    final StringBuilder buffer = new StringBuilder(ruler.getLength());
    treeToBuffer(buffer, root, 0, skipWhitespaces, false, false, true);
    return buffer.toString();
  }

  public static String nodeTreeToString(@NotNull final ASTNode root, final boolean skipWhitespaces) {
    final LengthBuilder ruler = new LengthBuilder();
    treeToBuffer(ruler, root, 0, skipWhitespaces, false, false, false);
    final StringBuilder buffer = new StringBuilder(ruler.getLength());
    treeToBuffer(buffer, root, 0, skipWhitespaces, false, false, false);
    return buffer.toString();
  }

  public static String treeToString(@NotNull ASTNode root, boolean skipWhitespaces, boolean showRanges) {
    final LengthBuilder ruler = new LengthBuilder();
    treeToBuffer(ruler, root, 0, skipWhitespaces, showRanges, false, true);
    final StringBuilder buffer = new StringBuilder(ruler.getLength());
    treeToBuffer(buffer, root, 0, skipWhitespaces, showRanges, false, true);
    return buffer.toString();
  }

  public static String treeToStringWithUserData(TreeElement root, boolean skipWhitespaces) {
    final LengthBuilder ruler = new LengthBuilder();
    treeToBufferWithUserData(ruler, root, 0, skipWhitespaces);
    final StringBuilder buffer = new StringBuilder(ruler.getLength());
    treeToBufferWithUserData(buffer, root, 0, skipWhitespaces);
    return buffer.toString();
  }

  public static String treeToStringWithUserData(PsiElement root, boolean skipWhitespaces) {
    final LengthBuilder ruler = new LengthBuilder();
    treeToBufferWithUserData(ruler, root, 0, skipWhitespaces);
    final StringBuilder buffer = new StringBuilder(ruler.getLength());
    treeToBufferWithUserData(buffer, root, 0, skipWhitespaces);
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
                                  PairConsumer<PsiElement, Consumer<PsiElement>> extra) {
    if (skipWhiteSpaces && root.getElementType() == TokenType.WHITE_SPACE) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    try {
      PsiElement psiElement = null;
      if (root instanceof CompositeElement) {
        if (usePsi) {
          psiElement = root.getPsi();
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
      if (root instanceof CompositeElement) {
        ASTNode child = root.getFirstChildNode();

        if (child == null) {
          StringUtil.repeatSymbol(buffer, ' ', indent + 2);
          buffer.append("<empty list>\n");
        }
        else {
          while (child != null) {
            treeToBuffer(buffer, child, indent + 2, skipWhiteSpaces, showChildrenRanges, showChildrenRanges, usePsi, extra);
            child = child.getTreeNext();
          }
        }
      }
      if (psiElement != null && extra != null ) {
        extra.consume(psiElement, new Consumer<PsiElement>() {
          @Override
          public void consume(PsiElement element) {
            treeToBuffer(buffer, element.getNode(), indent + 2, skipWhiteSpaces, showChildrenRanges, showChildrenRanges, usePsi, null);
          }
        });
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static String lightTreeToString(@NotNull final FlyweightCapableTreeStructure<LighterASTNode> tree,
                                         final boolean skipWhitespaces) {
    final LengthBuilder ruler = new LengthBuilder();
    lightTreeToBuffer(tree, tree.getRoot(), ruler, 0, skipWhitespaces);
    final StringBuilder buffer = new StringBuilder(ruler.getLength());
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
        final Ref<LighterASTNode[]> kids = new Ref<LighterASTNode[]>();
        final int numKids = tree.getChildren(tree.prepareForGetChildren(node), kids);
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
    final LengthBuilder ruler = new LengthBuilder();
    stubTreeToBuffer(root, ruler, 0);
    final StringBuilder builder = new StringBuilder(ruler.getLength());
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
    final LengthBuilder ruler = new LengthBuilder();
    psiToBuffer(ruler, root, skipWhiteSpaces, showRanges, extra);
    final StringBuilder buffer = new StringBuilder(ruler.getLength());
    psiToBuffer(buffer, root, skipWhiteSpaces, showRanges, extra);
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
        extra.consume(root, new Consumer<PsiElement>() {
          @Override
          public void consume(PsiElement element) {
            psiToBuffer(buffer, element, indent + 2, skipWhiteSpaces, showChildrenRanges, showChildrenRanges, null);
          }
        });
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
    return ExceptionUtil.getThrowableText(new Throwable());
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

  private static final Key<List<Pair<Object, Processor<PsiElement>>>> TRACK_INVALIDATION_KEY = Key.create("TRACK_INVALIDATION_KEY");
  private static volatile boolean ourTrackInvalidationCalled = false;

  public static void trackInvalidation(@NotNull PsiElement element, @NotNull Object requestor, @NotNull Processor<PsiElement> callback) {
    ourTrackInvalidationCalled = true;
    synchronized (element) {
      final ASTNode node = element.getNode();
      if (node == null) return;
      List<Pair<Object, Processor<PsiElement>>> callbacks = node.getUserData(TRACK_INVALIDATION_KEY);
      if (callbacks == null) {
        callbacks = new SmartList<Pair<Object, Processor<PsiElement>>>();
        node.putUserData(TRACK_INVALIDATION_KEY, callbacks);
      }
      for (int i = 0; i < callbacks.size(); i++) {
        Pair<Object, Processor<PsiElement>> pair = callbacks.get(i);
        Object callbackRequestor = pair.first;
        if (callbackRequestor.equals(requestor)) {
          callbacks.set(i, Pair.create(requestor, callback));
          return;
        }
      }
      callbacks.add(Pair.create(requestor, callback));
    }
  }

  public static void onInvalidated(@NotNull TreeElement treeElement) {
    if (!ourTrackInvalidationCalled) {
      return;
    }

    treeElement.acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
      @Override
      protected void visitNode(TreeElement element) {
        List<Pair<Object, Processor<PsiElement>>> callbacks = element.getUserData(TRACK_INVALIDATION_KEY);
        if (callbacks != null) {
          for (Pair<Object, Processor<PsiElement>> pair : callbacks) {
            Processor<PsiElement> callback = pair.second;
            PsiElement psi = element.getPsi();
            if (psi != null) callback.process(psi);
          }
        }
        super.visitNode(element);
      }
    });
  }

  public static void sleep(long millis) {
    TimeoutUtil.sleep(millis);
  }
  public static void checkTreeStructure(ASTNode element) {
    if (CHECK){
      doCheckTreeStructure(element);
    }
  }

}
