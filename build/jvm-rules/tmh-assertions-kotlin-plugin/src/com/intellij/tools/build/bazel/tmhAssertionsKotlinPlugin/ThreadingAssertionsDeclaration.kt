// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.tmhAssertionsKotlinPlugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * The `ThreadingAssertions` class of the compile, as the compiler resolves it. The IR plugin context sees the compile
 * classpath and the Kotlin and Java sources of the module, so the module that declares the class gets the assertions
 * too. The pass runs before the code generation and reads one symbol; it changes no IR.
 */
internal class ThreadingAssertionsDeclaration : IrGenerationExtension {
  /**
   * The names of the public static methods without parameters and without a result, or null when the compile has no
   * `ThreadingAssertions` class. The assertion generators select themselves by these names.
   */
  @Volatile
  var assertionMethods: Set<String>? = null
    private set

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val file = moduleFragment.files.firstOrNull() ?: return
    val symbol = pluginContext.finderForSource(file).findClass(ClassId.topLevel(FqName(CLASS_NAME))) ?: return
    val owner = symbol.owner
    assertionMethods = owner.declarations
      .filterIsInstance<IrSimpleFunction>()
      .filter { it.visibility == DescriptorVisibilities.PUBLIC && it.returnType.isUnit() && isStaticWithoutParameters(it, owner) }
      .mapTo(HashSet()) { it.name.asString() }
  }

  /**
   * True for a function that the JVM backend writes as a static method without parameters: a static method of a Java
   * class, or a `@JvmStatic` function of an object.
   */
  private fun isStaticWithoutParameters(function: IrSimpleFunction, owner: IrClass): Boolean {
    val receiver = function.dispatchReceiverParameter
    val isStatic = receiver == null || (owner.kind == ClassKind.OBJECT && function.hasAnnotation(JVM_STATIC))
    return isStatic && function.parameters.none { it != receiver }
  }

  companion object {
    const val CLASS_NAME: String = "com.intellij.util.concurrency.ThreadingAssertions"
    private val JVM_STATIC = FqName("kotlin.jvm.JvmStatic")
  }
}
