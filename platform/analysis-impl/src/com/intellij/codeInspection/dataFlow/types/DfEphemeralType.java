// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;

/**
 * A value that is impossible with currently available source code but
 * could appear if separate compilation takes place. A common example of ephemeral value
 * is a constant of enum type that doesn't equal to any existing constant.
 * <p>
 * When ephemeral value is stored in the memory state variable we assume that the whole
 * memory state is ephemeral.
 *
 * @see DfaMemoryState#isEphemeral()
 */
public interface DfEphemeralType extends DfType {
}
