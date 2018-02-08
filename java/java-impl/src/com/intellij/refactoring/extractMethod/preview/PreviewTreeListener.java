// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.refactoring.util.duplicates.Match;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
interface PreviewTreeListener {
  void onFragmentSelected(FragmentNode node, List<DuplicateNode> enabledDuplicates);
}
