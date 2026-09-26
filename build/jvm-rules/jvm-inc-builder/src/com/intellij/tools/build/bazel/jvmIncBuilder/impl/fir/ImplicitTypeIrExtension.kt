// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.fir

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ImplicitTypeDependencyTracker
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef

/**
 * IR generation extension that analyzes compiled declarations to find
 * public API functions and properties with inferred (implicit) types.
 *
 * This extension runs after FIR analysis and works with the fully resolved IR tree.
 * It detects public/protected/internal declarations where the return type is inferred
 * (no explicit type annotation in source).
 *
 * This information is used for incremental compilation to ensure proper
 * recompilation when changes in dependencies could affect the inferred type.
 */
class ImplicitTypeIrExtension(
  private val tracker: ImplicitTypeDependencyTracker
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    @Suppress("DEPRECATION")
    moduleFragment.acceptVoid(InferredTypeVisitor(tracker))
  }
}

/**
 * Visitor that traverses the IR tree to find public API declarations with inferred types.
 */
@Suppress("DEPRECATION")
private class InferredTypeVisitor(
  private val tracker: ImplicitTypeDependencyTracker
) : IrVisitorVoid() {

  private var currentFilePath: String? = null

  override fun visitElement(element: IrElement) {
    element.acceptChildrenVoid(this)
  }

  override fun visitFile(declaration: IrFile) {
    val filePath = declaration.fileEntry.name
    currentFilePath = filePath
    if (!tracker.isFileRecorded(filePath)) {
      declaration.acceptChildrenVoid(this)
    }
    currentFilePath = null
  }

  override fun visitSimpleFunction(declaration: IrSimpleFunction) {
    val currentPath = currentFilePath
    if (currentPath != null && !tracker.isFileRecorded(currentPath)) {
      processFunction(declaration)
    }
    declaration.acceptChildrenVoid(this)
  }

  override fun visitProperty(declaration: IrProperty) {
    val currentPath = currentFilePath
    if (currentPath != null && !tracker.isFileRecorded(currentPath)) {
      processProperty(declaration)
    }
    declaration.acceptChildrenVoid(this)
  }

  private fun processFunction(function: IrSimpleFunction) {
    if (!isPublicApi(function)) return
    if (function.isLocal) return
    if (function.returnType.isUnit()) return
    if (hasExplicitReturnType(function)) return  // Skip functions with explicit return type

    val filePath = currentFilePath ?: return
    // Record file as having a public API function with inferred return type
    tracker.recordFileWithInferredExternalType(filePath)
  }

  /**
   * Checks if a function has an explicitly specified return type in the source code.
   * Uses the original FIR element's returnTypeRef.source.kind to determine if type was explicit.
   * For implicit types, source.kind is KtFakeSourceElementKind.ImplicitTypeRef.
   * Returns true (conservative) if unable to determine, to avoid false positives.
   */
  private fun hasExplicitReturnType(function: IrSimpleFunction): Boolean {
    val firFunction = (function.metadata as? FirMetadataSource.Function)?.fir ?: return true  // Can't determine → assume explicit (conservative)
    val returnTypeRef = firFunction.returnTypeRef as? FirResolvedTypeRef ?: return true
    // For implicit types, source.kind is KtFakeSourceElementKind.ImplicitTypeRef
    return returnTypeRef.source?.kind != KtFakeSourceElementKind.ImplicitTypeRef
  }

  private fun processProperty(property: IrProperty) {
    if (!isPublicApi(property)) return
    if (property.isLocal) return
    if (hasExplicitPropertyType(property)) return  // Skip properties with explicit type

    val filePath = currentFilePath ?: return
    // Record file as having a public API property with inferred type
    tracker.recordFileWithInferredExternalType(filePath)
  }

  /**
   * Checks if a property has an explicitly specified type in the source code.
   * Uses the original FIR element's returnTypeRef.source.kind to determine if type was explicit.
   * For implicit types, source.kind is KtFakeSourceElementKind.ImplicitTypeRef.
   * Returns true (conservative) if unable to determine, to avoid false positives.
   */
  private fun hasExplicitPropertyType(property: IrProperty): Boolean {
    val firProperty = (property.metadata as? FirMetadataSource.Property)?.fir ?: return true  // Can't determine → assume explicit (conservative)
    val returnTypeRef = firProperty.returnTypeRef as? FirResolvedTypeRef ?: return true
    // For implicit types, source.kind is KtFakeSourceElementKind.ImplicitTypeRef
    return returnTypeRef.source?.kind != KtFakeSourceElementKind.ImplicitTypeRef
  }

  private fun isPublicApi(declaration: IrDeclarationWithVisibility): Boolean {
    return declaration.visibility == DescriptorVisibilities.PUBLIC ||
           declaration.visibility == DescriptorVisibilities.PROTECTED ||
           declaration.visibility == DescriptorVisibilities.INTERNAL
  }
}
