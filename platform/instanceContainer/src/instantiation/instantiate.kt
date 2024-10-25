// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.instantiation

import com.intellij.concurrency.installTemporaryThreadContext
import com.intellij.openapi.progress.Cancellation
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ArrayUtilRt
import com.intellij.util.containers.toArray
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor

/**
 * Instantiates [instanceClass] using [resolver] to find instances for constructor parameter types.
 * This function searches for a constructor which matches one of [supportedSignatures].
 *
 * @param parentScope a scope which is used as a parent for instance scope
 * if [instanceClass] defines a constructor with a parameter of [CoroutineScope] type
 * @param supportedSignatures a list of constructor signatures which are looked up in the class.
 * @throws InstantiationException if none class does not define any constructor with one of [supportedSignatures],
 * or [resolver] cannot find an instance for constructor parameter type
 */
suspend fun <T> instantiate(
  resolver: DependencyResolver,
  parentScope: CoroutineScope,
  instanceClass: Class<T>,
  supportedSignatures: List<MethodType>,
): T {
  val (signature, constructor) = findConstructor(instanceClass, supportedSignatures)
  when (val result = resolveArguments(resolver = resolver,
                                      parameterTypes = signature.parameterArray(),
                                      instanceClass = instanceClass,
                                      round = 0)) {
    is ResolutionResult.UnresolvedParameter -> {
      throw InstantiationException(
        "Signature '$signature' for found in '${instanceClass.name}', but '${resolver}' cannot resolve '${result.parameterType}'"
      )
    }
    is ResolutionResult.Resolved -> {
      return instantiate(parentScope, instanceClass, result.arguments) { args ->
        if (args.isEmpty()) {
          @Suppress("UNCHECKED_CAST")
          constructor.invoke() as T
        }
        else {
          @Suppress("UNCHECKED_CAST")
          constructor.invokeWithArguments(*args) as T
        }
      }
    }
  }
}

private fun findConstructor(instanceClass: Class<*>, signatures: List<MethodType>): Pair<MethodType, MethodHandle> {
  val lookup = MethodHandles.privateLookupIn(instanceClass, MethodHandles.lookup())
  for (signature in signatures) {
    try {
      return Pair(signature, lookup.findConstructor(instanceClass, signature))
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: NoSuchMethodException) { }
    catch (_: IllegalAccessException) { }
    catch (e: Throwable) { throw e }
  }
  throw InstantiationException("Class '$instanceClass' does not define any of supported signatures '$signatures'")
}

/**
 * Instantiates [instanceClass] using [resolver] to find instances for constructor parameter types.
 * This function searches for the greediest constructor, i.e., a constructor with most parameters, which is satisfiable.
 *
 * @param parentScope a scope which is used as a parent for instance scope
 * if [instanceClass] defines a constructor with a parameter of [CoroutineScope] type
 * @throws InstantiationException if there are no constructors,
 * or more than one satisfiable constructor,
 * or [resolver] cannot find an instance for constructor parameter type
 */
suspend fun <T> instantiate(resolver: DependencyResolver, parentScope: CoroutineScope, instanceClass: Class<T>): T {
  val (constructor, lazyArgs) = findConstructorAndArguments(resolver, instanceClass)
  return instantiate(parentScope, instanceClass, lazyArgs) {
    constructor.isAccessible = true
    constructor.newInstance(*it)
  }
}

private fun <T> findConstructorAndArguments(
  resolver: DependencyResolver,
  instanceClass: Class<T>,
): DependencyResolutionResult.Resolved<T> {
  when (val result = findConstructorAndArguments(resolver, instanceClass, rounds = 3)) {
    is DependencyResolutionResult.Resolved -> {
      return result
    }
    is DependencyResolutionResult.Ambiguous -> {
      // TODO ? log an error and choose first instead
      throw InstantiationException(
        "Too many satisfiable constructors: ${result.first}, ${result.second}",
      )
    }
    is DependencyResolutionResult.Failed -> {
      if (result.unsatisfiableConstructors.isEmpty()) {
        throw InstantiationException(
          "$instanceClass does not have an applicable constructor:\n" +
          instanceClass.declaredConstructors.joinToString(prefix = "\n", separator = "\n"),
        )
      }
      else {
        val dependencyString = result.unsatisfiableConstructors.joinToString(separator = ";\n") {
          "${it.constructor} has unsatisfied dependency type ${it.parameterType}"
        }
        throw InstantiationException(
          "$resolver cannot instantiate '${instanceClass.name}':\n$dependencyString\n",
        )
      }
    }
  }
}

private fun <T> findConstructorAndArguments(
  resolver: DependencyResolver,
  instanceClass: Class<T>,
  @Suppress("SameParameterValue") rounds: Int,
): DependencyResolutionResult<T> {
  val constructors = instanceClass.declaredConstructors

  @Suppress("UNCHECKED_CAST")
  val sortedConstructors = constructors.sortedBy {
    -it.parameterCount
  } as List<Constructor<T>>

  var roundIndex = 0
  val roundZero = doFindConstructorAndArguments(resolver = resolver,
                                                constructors = sortedConstructors,
                                                round = roundIndex,
                                                instanceClass = instanceClass)
  var round = roundZero
  while (true) {
    when (round) {
      is DependencyResolutionResult.Resolved -> {
        return round
      }
      is DependencyResolutionResult.Ambiguous -> {
        return round
      }
      is DependencyResolutionResult.Failed -> {
        when {
          round.unsatisfiableConstructors.isEmpty() -> {
            return round
          }
          roundIndex < rounds -> {
            roundIndex++
            round = doFindConstructorAndArguments(resolver = resolver,
                                                  constructors = sortedConstructors,
                                                  instanceClass = instanceClass,
                                                  round = roundIndex)
          }
          else -> {
            // NB reporting unsatisfiable constructors from round zero
            // because we ultimately want the constructors to be satisfied on the first try
            return roundZero as DependencyResolutionResult.Failed
          }
        }
      }
    }
  }
}

private fun <T> doFindConstructorAndArguments(
  resolver: DependencyResolver,
  constructors: List<Constructor<T>>,
  instanceClass: Class<T>,
  round: Int,
): DependencyResolutionResult<T> {
  var greediest: DependencyResolutionResult.Resolved<T>? = null
  var unsatisfiableConstructors: MutableList<UnsatisfiedConstructorParameterType<T>>? = null

  val singleConstructor = constructors.size == 1

  constructors@
  for (constructor in constructors) {
    if (constructor.isSynthetic) {
      continue@constructors
    }
    if (!singleConstructor && !resolver.isApplicable(constructor)) {
      continue@constructors
    }

    val parameterTypes = constructor.parameterTypes
    if (greediest != null && parameterTypes.size < greediest.arguments.size) {
      // the next constructor has strictly fewer parameters than previous
      return greediest
    }

    // first, perform fast check to ensure that assert about getComponentAdapterOfType is thrown only if the constructor is applicable
    if (!parameterTypes.all { resolver.isInjectable(it) }) {
      continue
    }

    val arguments = when (val result = resolveArguments(resolver = resolver,
                                                        parameterTypes = parameterTypes,
                                                        instanceClass = instanceClass,
                                                        round = round)) {
      is ResolutionResult.UnresolvedParameter -> {
        if (unsatisfiableConstructors == null) {
          unsatisfiableConstructors = ArrayList()
        }
        unsatisfiableConstructors.add(UnsatisfiedConstructorParameterType(constructor, result.parameterType))
        continue@constructors // unsatisfiable constructor
      }
      is ResolutionResult.Resolved -> {
        result.arguments
      }
    }
    check(arguments.size == parameterTypes.size)
    if (greediest == null) {
      greediest = DependencyResolutionResult.Resolved(constructor, arguments)
    }
    else if (parameterTypes.size == greediest.arguments.size) {
      // size as the previous one
      return DependencyResolutionResult.Ambiguous(greediest, DependencyResolutionResult.Resolved(constructor, arguments))
    }
    else {
      // sanity check
      error("Greediest constructor should've been returned already")
    }
  }

  return greediest
         ?: DependencyResolutionResult.Failed(unsatisfiableConstructors ?: emptyList())
}

private sealed interface ResolutionResult {

  @JvmInline
  value class UnresolvedParameter(val parameterType: Class<*>) : ResolutionResult

  @JvmInline
  value class Resolved(val arguments: List<Argument>) : ResolutionResult
}

private fun resolveArguments(resolver: DependencyResolver,
                             parameterTypes: Array<Class<*>>,
                             instanceClass: Class<*>,
                             round: Int): ResolutionResult {
  if (parameterTypes.isEmpty()) {
    return ResolutionResult.Resolved(emptyList())
  }

  val arguments = ArrayList<Argument>(parameterTypes.size)
  for (parameterType in parameterTypes) {
    if (parameterType === CoroutineScope::class.java) {
      arguments.add(Argument.CoroutineScopeMarker)
    }
    else {
      val dependency = resolver.resolveDependency(parameterType = parameterType, instanceClass = instanceClass, round = round)
                       ?: return ResolutionResult.UnresolvedParameter(parameterType)
      arguments.add(Argument.LazyArgument(dependency))
    }
  }
  return ResolutionResult.Resolved(arguments)
}

private suspend fun <T> instantiate(
  parentScope: CoroutineScope,
  instanceClass: Class<T>,
  lazyArgs: List<Argument>,
  instantiate: (Array<out Any>) -> T,
): T {
  val args: Array<Any> = if (lazyArgs.isEmpty()) {
    ArrayUtilRt.EMPTY_OBJECT_ARRAY
  }
  else {
    coroutineScope {
      lazyArgs.map { argument: Argument ->
        if (argument === Argument.CoroutineScopeMarker) {
          CompletableDeferred(argument)
        }
        else {
          val supplier = (argument as Argument.LazyArgument).argumentSupplier
          async(start = CoroutineStart.UNDISPATCHED) { // don't pay for dispatch
            supplier()
          }
        }
      }.awaitAll()
    }
      .toArray(ArrayUtilRt.EMPTY_OBJECT_ARRAY)
      .also { args ->
        replaceScopeMarkerWithScope(args) {
          parentScope.childScope(instanceClass.name)
        }
      }
  }
  // If a service is requested during highlighting (under impatient=true),
  // then it's initialization might be broken forever.
  // Impatient reader is a property of thread (at the moment, before IJPL-53 is completed),
  // so it leaks to newInstance call, where it might cause ReadMostlyRWLock.throwIfImpatient() to throw,
  // for example, if a service obtains a read action in the constructor.
  // Non-cancellable section is required to silence throwIfImpatient().
  // In general, we want initialization to be cancellable, and it must be canceled only on parent scope cancellation,
  // which happens only on project/application shutdown, or on plugin unload.
  Cancellation.withNonCancelableSection().use {
    return withStoredTemporaryContext(parentScope) {
      instantiate(args)
    }
  }
}


// A separate thread-local is required to track cyclic service initialization, because
// we don't want it to be captured by lambdas scheduled in the constructor (= context propagation).
// Only the context of the owner coroutine should be captured.
// TODO Put BlockingJob to bind all computations started in instance constructor to instance scope.
@ApiStatus.Internal
suspend fun <T> withStoredTemporaryContext(parentScope: CoroutineScope, action: () -> T): T {
  val existingCoroutineContext = currentCoroutineContext()
  val scopeContext = parentScope.coroutineContext
  // `temporaryCoroutineContext` belongs to the initialization processes throughout the whole chain of initialization.
  // We want to prevent the elements that are relevant to `parentScope` from leaking into the nested initialization processes.
  val curatedContext = scopeContext.fold(existingCoroutineContext) { newCtx, key -> newCtx.minusKey(key.key) }
  return installTemporaryThreadContext(curatedContext).use {
    action()
  }
}

private fun replaceScopeMarkerWithScope(args: Array<Any>, instanceScope: () -> CoroutineScope) {
  var scope: CoroutineScope? = null
  for ((index, arg) in args.withIndex()) {
    if (arg === Argument.CoroutineScopeMarker) {
      if (scope == null) {
        scope = instanceScope()
      }
      args[index] = scope
    }
  }
}
