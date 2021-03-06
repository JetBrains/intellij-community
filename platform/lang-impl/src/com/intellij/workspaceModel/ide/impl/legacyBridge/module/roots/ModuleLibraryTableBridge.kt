package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.LibraryTable

interface ModuleLibraryTableBridge: LibraryTable {
  val module: Module
}