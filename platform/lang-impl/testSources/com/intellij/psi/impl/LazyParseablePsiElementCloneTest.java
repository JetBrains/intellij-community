// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for IJPL-18557. Cloning a {@link LazyParseablePsiElement} that has not yet been parsed
 * must not propagate the {@code !isParsed()} flag onto a clone that then receives copied children, which
 * would trigger {@code "Mutating collapsed chameleon"} in
 * {@link com.intellij.psi.impl.source.tree.LazyParseableElement#rawAddChildrenWithoutNotifications}.
 */
public class LazyParseablePsiElementCloneTest extends BareTestFixtureTestCase {
  private static final IElementType LEAF_TYPE = new IElementType("TEST_LEAF", Language.ANY);

  private static final ILazyParseableElementType LAZY_TYPE = new ILazyParseableElementType("TEST_LAZY", Language.ANY) {
    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      return null;
    }
  };

  @Test
  public void cloningUnparsedChameleonDoesNotParseOriginal() {
    LazyParseablePsiElement chameleon = new LazyParseablePsiElement(LAZY_TYPE, "abc");
    assertFalse(chameleon.isParsed());

    LazyParseablePsiElement clone = chameleon.clone();

    assertNotSame(chameleon, clone);
    assertFalse("original must remain unparsed after clone", chameleon.isParsed());
    assertFalse("clone of an unparsed chameleon must itself be unparsed", clone.isParsed());
    assertEquals(LAZY_TYPE, clone.getElementType());
    assertEquals("abc", clone.getText());
  }

  @Test
  public void cloningChameleonInBrokenStateDoesNotMutateCollapsedChameleon() throws Exception {
    // Reproduce the production scenario: an unparsed chameleon that nonetheless has a raw
    // child reference. There is no public API that produces this state, so we inject it
    // through reflection on package-private fields of CompositeElement / TreeElement.
    LazyParseablePsiElement chameleon = new LazyParseablePsiElement(LAZY_TYPE, "abc");
    LeafElement leaf = new LeafPsiElement(LEAF_TYPE, "abc");

    setField(CompositeElement.class, chameleon, "firstChild", leaf);
    setField(CompositeElement.class, chameleon, "lastChild", leaf);
    setField(TreeElement.class, leaf, "myParent", chameleon);

    assertFalse(chameleon.isParsed());

    // Without the fix, super.clone() would push a child clone into a still-unparsed clone and fail the test.
    LazyParseablePsiElement clone = chameleon.clone();

    assertNotSame(chameleon, clone);
    assertFalse(clone.isParsed());

    // Use raw field access rather than getFirstChildNode(), which would force the clone
    // to be parsed (defeating the assertion we want to make).
    Field firstChild = CompositeElement.class.getDeclaredField("firstChild");
    firstChild.setAccessible(true);
    assertNull("cloneWithoutCopyingChildren must leave clone.firstChild null", firstChild.get(clone));
  }

  @Test
  public void cloningParsedChameleonCopiesChildren() {
    // Constructing a chameleon with null text marks it as already parsed (see LazyParseableElement
    // constructor). We can then attach a child via the normal raw API — the isParsed() check in
    // rawAddChildrenWithoutNotifications passes because the receiver is parsed.
    LazyParseablePsiElement chameleon = new LazyParseablePsiElement(LAZY_TYPE, null);
    assertTrue(chameleon.isParsed());

    LeafElement leaf = new LeafPsiElement(LEAF_TYPE, "abc");
    chameleon.rawAddChildren(leaf);

    LazyParseablePsiElement clone = chameleon.clone();

    assertNotSame(chameleon, clone);
    assertTrue("clone of a parsed chameleon must itself be parsed", clone.isParsed());
    assertEquals(LAZY_TYPE, clone.getElementType());

    // Children must be deep-copied: the clone has a structurally equivalent child, not the same node.
    ASTNode cloneFirst = clone.getFirstChildNode();
    assertNotNull("parsed-branch clone must copy children", cloneFirst);
    assertNotSame(leaf, cloneFirst);
    assertEquals(LEAF_TYPE, cloneFirst.getElementType());
    assertEquals("abc", cloneFirst.getText());
    assertEquals("abc", clone.getText());
  }

  private static void setField(@NotNull Class<?> owner, @NotNull Object target, @NotNull String name, Object value) throws Exception {
    Field f = owner.getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }
}
