package com.jetbrains.rhizomedb.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.JvmPlatform

abstract class DeclarationBasedExtension(
  protected val pluginContext: IrPluginContext,
  protected val moduleFragment: IrModuleFragment
) {

  private val cacheFun = mutableMapOf<CallableId, IrSimpleFunctionSymbol>()
  private val cacheClass = mutableMapOf<ClassId, IrClassSymbol>()

  protected fun funByName(name: String, prefix: FqName): IrSimpleFunctionSymbol {
    val callableId = CallableId(prefix, Name.identifier(name))
    return cacheFun.computeIfAbsent(callableId) {
      pluginContext.referenceFunctions(it).singleOrNull() ?: throw SymbolNotFoundException(it.toString(), moduleFragment)
    }
  }

  protected fun classByName(name: String, prefix: FqName): IrClassSymbol {
    val classId = ClassId.topLevel(prefix.child(Name.identifier(name)))
    return cacheClass.computeIfAbsent(classId) {
      pluginContext.referenceClass(it) ?: throw SymbolNotFoundException(it.asString(), moduleFragment)
    }
  }

  /**
   * Return a list of all IrDeclaration in the module.
   */
  protected val declarations by lazy {
    val declarations = mutableListOf<IrDeclaration>()
    moduleFragment.accept(object : IrVisitorVoid() {
      override fun visitElement(element: IrElement) {
        if (element is IrDeclaration) {
          declarations += element
        }
        element.acceptChildren(this, null)
      }
    }, null)
    declarations
  }

  /**
   * This returns true if the current target includes any JVM specific code.
   */
  protected fun includesJvmTarget() = pluginContext.platform?.any { it is JvmPlatform } == true

  /**
   * This returns true if the current target includes any non-JVM specific code.
   */
  protected fun includesNonJvmTarget() = pluginContext.platform?.any { it !is JvmPlatform } == true

  protected fun IrClass.jvmLikeName(): String {
    return when (val thisParent = parent) {
      is IrClass -> "${thisParent.jvmLikeName()}\$${name}"
      else -> kotlinFqName.toString()
    }
  }
}

class SymbolNotFoundException(fqn: String, module: IrModuleFragment) :
  RuntimeException("$fqn cannot be resolved from module `${module.name.asString()}`.")
