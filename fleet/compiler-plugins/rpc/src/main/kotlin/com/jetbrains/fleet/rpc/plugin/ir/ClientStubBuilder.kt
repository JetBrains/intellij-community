package com.jetbrains.fleet.rpc.plugin.ir

import com.jetbrains.fleet.rpc.plugin.ir.util.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.codegen.AnnotationCodegen.Companion.annotationClass
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addDefaultGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val flowPackage = FqName.fromSegments(listOf("kotlinx", "coroutines", "flow"))
private val flowClassId = ClassId(packageFqName = flowPackage, topLevelName = "Flow".name)
private val flowCollectorClassId = ClassId(packageFqName = flowPackage, topLevelName = "FlowCollector".name)
private val flowFnId = CallableId(packageName = flowPackage, callableName = "flow".name)
private val flowCollectFnId = CallableId(classId = flowClassId, callableName = "collect".name)

private val asyncPackage = FqName.fromSegments(listOf("fleet", "util", "async"))
private val resourceFnId = CallableId(packageName = asyncPackage, callableName = "resource".name)
private val resourceUseFnId = CallableId(classId = ClassId(asyncPackage, "Resource".name), callableName = "use".name)
private val consumedClassId = ClassId(asyncPackage, "Consumed".name)

private val coroutineScopeClassId = ClassId(FqName.fromSegments(listOf("kotlinx", "coroutines")), "CoroutineScope".name)

fun buildClientStub(interfaceClass: IrClass, rpcMethods: List<IrSimpleFunction>, context: CompilerPluginContext): IrClass {
  val pluginContext = context.pluginContext

  // Resolve necessary refs
  val flowCollectorClass = pluginContext.referenceClass(flowCollectorClassId)!!
  val flowFunction = pluginContext.referenceFunctions(flowFnId).first()
  val collectFunction = pluginContext.referenceFunctions(flowCollectFnId).first()
  val resourceFunction = pluginContext.referenceFunctions(resourceFnId).first()
  val useFunction = pluginContext.referenceFunctions(resourceUseFnId).first()
  val consumed = pluginContext.referenceClass(consumedClassId)!!
  val coroutineScope = pluginContext.referenceClass(coroutineScopeClassId)!!

  val generatedClass = pluginContext.buildClassBase {
    name = Name.identifier(interfaceClass.name.identifier + "ClientStub")
    visibility = interfaceClass.visibility
  }.also {
    it.parent = interfaceClass.parent
    it.superTypes = listOfNotNull(interfaceClass.defaultType)
    it.annotations += interfaceClass.extractApiStatusAnnotations()

    val container = interfaceClass.parent as? IrClass ?: interfaceClass.file
    container.addChild(it)
  }

  val invocationHandler = generatedClass.addInvocationHandlerToPrimaryConstructor(pluginContext)

  for (rpcMethod in rpcMethods) {
    val originalFunction = rpcMethod.symbol.owner
    pluginContext.addFunctionOverride(originalFunction, generatedClass) { generatedFunction ->
      generatedFunction.annotations += originalFunction.extractApiStatusAnnotations()
      generatedFunction.generateBody(context) {
        val irThis = irGet(generatedFunction.parameters[0])

        // Obtain the 'invoke' function from the 'invocationHandler'
        val invokeFunction = invocationHandler.backingField!!.type.getClass()!!.functions.first { it.name.asString() == "invoke" }

        val invocationHandlerReceiver = irGetValue(invocationHandler, irThis) // this.invocationHandler
        val invocationHandlerCall = irCall(invokeFunction.symbol).also { call -> // this.invocationHandler(methodName, args)
          call.arguments[0] = invocationHandlerReceiver
          call.arguments[1] = irString(originalFunction.name.identifier) // methodName

          val args = createArrayOfExpression(
            pluginContext.irBuiltIns.anyNType,
            // All parameters except the first (dispatch receiver)
            generatedFunction.parameters.drop(1).map { irGet(it) },
            pluginContext
          )
          call.arguments[2] = args // args
        }

        when {
          originalFunction.isNonSuspendFlowFunction() -> {
            val flowElementType = when (val arg = (originalFunction.returnType as IrSimpleType).arguments.first()) {
              is IrStarProjection -> pluginContext.irBuiltIns.anyNType
              is IrTypeProjection -> arg.type
            }

            // flow { it -> invocationHandler(...).collect(it) }
            val wrappedCall = irCall(flowFunction).also { flowCall ->
              val flowLambda = pluginContext.irLambda(
                originalFunction,
                parent = generatedFunction,
                pluginContext.irBuiltIns.suspendFunctionN(1).typeWith(
                  flowCollectorClass.typeWith(flowElementType),
                  pluginContext.irBuiltIns.unitType
                )
              ) { flowLambda ->
                flowLambda.generateBody(context) {
                  +irReturn(
                    irCall(collectFunction).also { collectCall ->
                      collectCall.arguments[0] = invocationHandlerCall

                      // We're getting a flow collector in the lambda, just gotta pass it
                      collectCall.arguments[1] = irGet(flowLambda.parameters.first())
                    }
                  )
                }
              }

              flowCall.arguments[0] = flowLambda
            }

            +irReturn(wrappedCall)
          }
          originalFunction.isNonSuspendResourceFunction() -> {
            val resourceElementType =
              ((originalFunction.returnType as IrSimpleType)
                .arguments
                .first()
                as IrTypeProjection
              ).type

            /*
             * resource { consume -> remoteCall().use { it -> consume(it) } }
             */
            val wrappedCall = irCall(resourceFunction).also { flowCall ->
              val consumerType = pluginContext.irBuiltIns.suspendFunctionN(1).typeWith(
                resourceElementType,
                consumed.defaultType,
              )
              val resourceLambda = pluginContext.irLambda(
                originalFunction,
                parent = generatedFunction,

                // suspend CoroutineScope.(consumer: Consumer<T>) -> Consumed
                pluginContext.irBuiltIns.suspendFunctionN(2).typeWith(
                  coroutineScope.defaultType,
                  consumerType,
                  consumed.defaultType
                )
              ) { resourceLambda ->
                resourceLambda.generateBody(context) {
                  +irReturn(
                    // use { it -> consume(it) }
                    irCall(useFunction).also { useCall ->
                      useCall.arguments[0] = invocationHandlerCall
                      useCall.arguments[1] = pluginContext.irLambda(
                        originalFunction,
                        parent = resourceLambda,
                        // suspend CoroutineScope.(T) -> U
                        pluginContext.irBuiltIns.suspendFunctionN(2).typeWith(
                          coroutineScope.defaultType,
                          resourceElementType,
                          consumed.defaultType,
                        )
                      ) { useLambda ->
                        useLambda.generateBody(context) {
                          +irReturn(irCall(consumerType.classOrFail.getSimpleFunction("invoke")!!).also { collectCall ->
                            // Call the resource consumer (second parameter: coroutine scope is first)
                            collectCall.arguments[0] = irGet(resourceLambda.parameters[1])

                            // ... with our result
                            collectCall.arguments[1] = irGet(useLambda.parameters[1])
                          })
                        }
                      }
                    }
                  )
                }
              }

              flowCall.arguments[0] = resourceLambda
            }

            +irReturn(wrappedCall)
          }
          else -> {
            +irReturn(invocationHandlerCall)
          }
        }
      }
    }
  }

  return generatedClass
}

private fun irGetValue(irProperty: IrProperty, receiver: IrExpression?): IrCall =
  IrCallImpl.fromSymbolOwner(
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = irProperty.backingField!!.type,
    symbol = irProperty.getter!!.symbol,
    origin = IrStatementOrigin.GET_PROPERTY,
  ).apply {
    dispatchReceiver = receiver
  }

private fun IrClass.addInvocationHandlerToPrimaryConstructor(pluginContext: IrPluginContext): IrProperty {
  val primaryConstructor = this.primaryConstructor!!

  val invocationHandlerType = pluginContext.getProxyType()
  val invocationHandler = primaryConstructor.addValueParameter("invocationHandler".name, invocationHandlerType)

  return addIrValProperty(
    valName = invocationHandler.name,
    valType = invocationHandlerType,
    valInitializer = irGet(invocationHandler),
    pluginContext = pluginContext,
  )
}

internal fun IrAnnotationContainer.extractApiStatusAnnotations(): List<IrConstructorCall> {
  return annotations.filter { it.annotationClass.kotlinFqName.startsWith(FqName.fromSegments(listOf("org", "jetbrains", "annotations", "ApiStatus"))) }
}

private fun IrClass.addIrValProperty(
  valName: Name,
  valType: IrType,
  valInitializer: IrExpression,
  pluginContext: IrPluginContext,
): IrProperty {
  val irClass = this

  val irField = pluginContext.irFactory.buildField {
    name = valName
    type = valType
    origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
    visibility = DescriptorVisibilities.PRIVATE
  }.apply {
    parent = irClass
    initializer = pluginContext.irFactory.createExpressionBody(valInitializer)
  }

  val irProperty = irClass.addProperty {
    name = valName
    isVar = false
  }.apply {
    parent = irClass
    backingField = irField
    addDefaultGetter(irClass, pluginContext.irBuiltIns)
  }

  return irProperty
}
