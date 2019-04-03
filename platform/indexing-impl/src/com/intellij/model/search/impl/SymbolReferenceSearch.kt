// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.psi.search.searches.ExtensibleQueryFactory

internal object SymbolReferenceSearch : ExtensibleQueryFactory<SymbolReference, SearchSymbolReferenceParameters>()
