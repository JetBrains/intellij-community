/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package noria.plugin

import noria.plugin.k2.ComposeInternalPackage
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * In K1, the frontend used to annotate inferred composable lambdas with `@Composable`.
 * The K2 frontend instead uses a different type for composable lambdas. This pass adds
 * the annotation, since the backend expects it.
 */
class ComposableLambdaAnnotator(context: IrPluginContext) : IrVisitorVoid() {
    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitFunctionExpression(expression: IrFunctionExpression) {
        if (expression.type.isSyntheticComposableFunction()) {
            expression.function.mark()
        }
        super.visitFunctionExpression(expression)
    }

    override fun visitFunctionReference(expression: IrFunctionReference) {
        if (expression.type.isSyntheticComposableFunction() && expression.symbol.owner.visibility == DescriptorVisibilities.LOCAL) {
            expression.symbol.owner.mark()
        }
        super.visitFunctionReference(expression)
    }

    private val composableSymbol = context.referenceClass(NoriaRuntime.ComposableClassId) ?: error("Cannot find the Composable annotation class in the classpath")

    private fun IrFunction.mark() {
        if (!hasComposableAnnotation) {
            annotations = annotations + IrConstructorCallImpl.fromSymbolOwner(
                composableSymbol.owner.defaultType,
                composableSymbol.constructors.single(),
            )
        }
    }
}

fun IrType.isSyntheticComposableFunction(): Boolean =
  classOrNull?.owner?.let {
    it.name.asString().startsWith("ComposableFunction") &&
    it.packageFqName == ComposeInternalPackage
  } ?: false

