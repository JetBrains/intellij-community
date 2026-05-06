// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.icons.AllIcons
import org.eclipse.lsp4j.SymbolKind
import javax.swing.Icon

open class LspSymbolKindCustomizer {
  /**
   * Compare with [LspCompletionSupport.getIcon] for similar, but incompatible icon mapping
   */
  open fun getIcon(kind: SymbolKind): Icon? = when (kind) {
    SymbolKind.File -> AllIcons.FileTypes.Any_type
    SymbolKind.Module -> AllIcons.Nodes.Module
    SymbolKind.Namespace -> AllIcons.Nodes.Package
    SymbolKind.Package -> AllIcons.Nodes.Package
    SymbolKind.Class -> AllIcons.Nodes.Class
    SymbolKind.Method -> AllIcons.Nodes.Method
    SymbolKind.Property -> AllIcons.Nodes.Property
    SymbolKind.Field -> AllIcons.Nodes.Field
    SymbolKind.Constructor -> AllIcons.Nodes.Method
    SymbolKind.Enum -> AllIcons.Nodes.Enum
    SymbolKind.Interface -> AllIcons.Nodes.Interface
    SymbolKind.Function -> AllIcons.Nodes.Function
    SymbolKind.Variable -> AllIcons.Nodes.Variable
    SymbolKind.Constant -> AllIcons.Nodes.Constant
    SymbolKind.String -> AllIcons.Nodes.Constant
    SymbolKind.Number -> AllIcons.Nodes.Constant
    SymbolKind.Boolean -> AllIcons.Nodes.Constant
    SymbolKind.Array -> AllIcons.Nodes.Variable
    SymbolKind.Object -> AllIcons.Nodes.Class
    SymbolKind.Key -> AllIcons.Nodes.Property
    SymbolKind.Null -> AllIcons.Nodes.Constant
    SymbolKind.EnumMember -> AllIcons.Nodes.Field
    SymbolKind.Struct -> AllIcons.Nodes.Class
    SymbolKind.Event -> AllIcons.Nodes.Method
    SymbolKind.Operator -> AllIcons.Nodes.Method
    SymbolKind.TypeParameter -> AllIcons.Nodes.Parameter
  }

  open val supportedKinds: List<SymbolKind> = SymbolKind.entries
}