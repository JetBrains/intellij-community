// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Supplies the targets of the Copy Reference action.
 * <p>
 * A {@link com.intellij.ide.actions.copyReference.CopyReferenceTarget} is an entity that the action can copy to the clipboard.
 * A plugin supplies the targets in one of these ways:
 * <ul>
 *   <li>{@link com.intellij.ide.actions.copyReference.CopyReferenceTargetProvider} gives the targets at a caret in an editor.</li>
 *   <li>{@link com.intellij.ide.actions.copyReference.SymbolCopyReferenceTargetProvider} gives the targets of a symbol.</li>
 *   <li>{@link com.intellij.ide.actions.copyReference.CopyReferenceSymbol} is a symbol that gives its own target.</li>
 * </ul>
 * The action puts the chosen target into the clipboard. A custom {@link com.intellij.ide.PasteProvider} reads the target with
 * {@link com.intellij.ide.actions.copyReference.CopyReferenceTarget#fromTransferable}.
 */
package com.intellij.ide.actions.copyReference;
