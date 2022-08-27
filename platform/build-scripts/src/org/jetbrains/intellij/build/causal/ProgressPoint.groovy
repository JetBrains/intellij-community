// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.causal

import groovy.transform.TupleConstructor

@TupleConstructor
@SuppressWarnings('GrFinalVariableAccess')
class ProgressPoint {
  final String classFqn
  final int lineNumber
}
