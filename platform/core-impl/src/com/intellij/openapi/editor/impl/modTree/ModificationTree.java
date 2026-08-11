// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.modTree;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// Persistent mapping between offsets in version 0 and offsets in the current version.
///
/// This structure stores only coordinate mapping.
///
/// It does not store:
///
/// - text
/// - intervals
/// - edit history
///
/// Semantics:
///
/// - deleted original text is represented as missing version-0 ranges
/// - inserted current text is represented as gaps between mapped original ranges
/// - mapping is right-biased
@ApiStatus.Internal
public interface ModificationTree {
  /// Maps an offset from version 0 to the current version.
  ///
  /// If the version-0 offset belongs to surviving original text, the result is
  /// the corresponding current offset.
  ///
  /// If the offset lies inside deleted original text, the result is the
  /// collapsed current position after the deleted or replaced region.
  int toCurrentOffset(int offsetInVersion0);

  /// Maps an offset from the current version back to version 0.
  ///
  /// If the current offset belongs to surviving original text, the result is
  /// the corresponding version-0 offset.
  ///
  /// If the current offset lies inside inserted text, the result is the
  /// version-0 boundary after that inserted gap.
  int toVersion0Offset(int offsetInCurrent);

  /// Returns a new tree representing insertion of `length` characters at
  /// `offsetInCurrent`.
  ///
  /// The current tree remains unchanged.
  ///
  /// `offsetInCurrent` must be in `[0, currentLength]`.
  /// `length` must be greater than or equal to zero.
  @NotNull
  @Contract(pure = true)
  ModificationTree insert(int offsetInCurrent, int length);

  /// Returns a new tree representing deletion of current-version text in
  /// `[startInCurrent, endInCurrent)`.
  ///
  /// The current tree remains unchanged.
  ///
  /// `startInCurrent` and `endInCurrent` must be in `[0, currentLength]`.
  /// `startInCurrent` must be less than or equal to `endInCurrent`.
  @NotNull
  @Contract(pure = true)
  ModificationTree delete(int startInCurrent, int endInCurrent);

  /// Checks internal structural invariants.
  ///
  /// Implementations should throw `IllegalStateException` if the tree is
  /// structurally invalid.
  ///
  /// This method is intended for tests, assertions, and debugging. It should
  /// not be called from hot lookup paths.
  void checkInvariants();
}