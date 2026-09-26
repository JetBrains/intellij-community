// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;

/**
 * A reference that a text match creates, not a language construct.
 * <p>
 * A plain name in a Markdown code span is an example. Such a reference resolves through a name match,
 * so it can point at a declaration that the author did not mean.
 * <p>
 * Find Usages reports such a reference only when the user keeps the text occurrence option.
 * The rename refactoring changes such a reference only under the same option.
 *
 * @see FindUsagesOptions#isSearchForTextOccurrences
 * @see FindUsagesHelper#isHiddenTextOccurrence(PsiReference, FindUsagesOptions)
 */
@ApiStatus.Experimental
public interface TextOccurrenceReference extends PsiReference {
}
