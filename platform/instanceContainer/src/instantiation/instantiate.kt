// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.instantiation

import com.intellij.openapi.progress.Cancellation
import com.intellij.platform.instanceContainer.internal.withCurrentlyInitializingHolder
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.toArray
import com.intellij.util.lateinitVal
import kotlinx.coroutines.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor

suspend fun <T> instantiate(
  resolver: DependencyResolver,
  parentScope: CoroutineScope,
  instanceClass: Class<T>,
  supportedSignatures: List<MethodType>,
): T {
  val (signature, constructor) = findConstructor(instanceClass, supportedSignatures)
  when (val result = resolveArguments(resolver, signature.parameterList(), round = 0)) {
    is ResolutionResult.UnresolvedParameter -> {
      throw InstantiationException(
        "Signature '$signature' for found in '${instanceClass.name}', but '${resolver}' cannot resolve '${result.parameterType}'"
      )
    }
    is ResolutionResult.Resolved -> {
      return instantiate(parentScope, instanceClass, result.arguments) {
        @Suppress("UNCHECKED_CAST")
        constructor.invokeWithArguments(*it) as T
      }
    }
  }
}

private fun findConstructor(instanceClass: Class<*>, signatures: List<MethodType>): Pair<MethodType, MethodHandle> {
  val lookup = MethodHandles.privateLookupIn(instanceClass, MethodHandles.lookup())
  for (signature in signatures) {
    runCatching {
      return Pair(
        signature,
        lookup.findConstructor(instanceClass, signature)
      )
    }
  }
  throw InstantiationException("Class '$instanceClass' does not define any of supported signatures '$signatures'")
}

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
  val roundZero = doFindConstructorAndArguments(resolver, sortedConstructors, roundIndex)
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
            round = doFindConstructorAndArguments(resolver, sortedConstructors, roundIndex)
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
  round: Int,
): DependencyResolutionResult<T> {
  var greediest: DependencyResolutionResult.Resolved<T>? = null
  var unsatisfiableConstructors: MutableList<ConstructorAndParameterType<T>>? = null

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
      // next constructor has strictly fewer parameters than previous
      return greediest
    }

    // first, perform fast check to ensure that assert about getComponentAdapterOfType is thrown only if the constructor is applicable
    if (!parameterTypes.all { resolver.isInjectable(it) }) {
      continue
    }

    val arguments = when (val result = resolveArguments(resolver, parameterTypes.toList(), round)) {
      is ResolutionResult.UnresolvedParameter -> {
        if (unsatisfiableConstructors == null) {
          unsatisfiableConstructors = ArrayList()
        }
        unsatisfiableConstructors.add(ConstructorAndParameterType(constructor, result.parameterType))
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

private fun resolveArguments(resolver: DependencyResolver, parameterTypes: List<Class<*>>, round: Int): ResolutionResult {
  val arguments = ArrayList<Argument>(parameterTypes.size)
  for (parameterType in parameterTypes) {
    arguments += if (parameterType === CoroutineScope::class.java) {
      Argument.CoroutineScopeMarker
    }
    else {
      val dependency = resolver.resolveDependency(parameterType, round)
      if (dependency != null) {
        Argument.LazyArgument(dependency)
      }
      else {
        return ResolutionResult.UnresolvedParameter(parameterType)
      }
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
  return coroutineScope {
    val args: Array<Any> = lazyArgs.map { argument: Argument ->
      if (argument === Argument.CoroutineScopeMarker) {
        CompletableDeferred(argument)
      }
      else {
        val supplier = (argument as Argument.LazyArgument).argumentSupplier
        async(start = CoroutineStart.UNDISPATCHED) { // don't pay for dispatch
          supplier()
        }
      }
    }.awaitAll().toArray(ArrayUtil.EMPTY_OBJECT_ARRAY)
    val initializerContext = currentCoroutineContext()
    var instanceResult: Result<T> by lateinitVal()
    parentScope.launch(CoroutineName(instanceClass.name), start = CoroutineStart.UNDISPATCHED) {
      // Don't cancel parentScope on failure.
      supervisorScope {
        val requiresScope = replaceScopeMarkerWithScope(args, this@supervisorScope)
        instanceResult = runCatching {
          // If a service is requested during highlighting (under impatient=true),
          // then it's initialization might be broken forever.
          // Impatient reader is a property of thread (at the moment, before IJPL-53 is completed),
          // so it leaks to newInstance call, where it might cause ReadMostlyRWLock.throwIfImpatient() to throw,
          // for example, if a service obtains a read action in the constructor.
          // Non-cancellable section is required to silence throwIfImpatient().
          // In general, we want initialization to be cancellable, and it must be cancelled only on parent scope cancellation,
          // which happens only on project/application shutdown, or on plugin unload.
          Cancellation.withNonCancelableSection().use {
            // A separate thread-local is required to track cyclic service initialization, because
            // we don't want it to be captured by lambdas scheduled in the constructor (= context propagation).
            // Only the context of the owner coroutine should be captured.
            withCurrentlyInitializingHolder(initializerContext).use {
              // TODO Put BlockingJob to bind all computations started in instance constructor to instance scope.
              instantiate(args)
            }
          }
        }
        if (instanceResult.isSuccess) {
          if (requiresScope) {
            awaitCancellation() // keep instance coroutine alive
          }
        }
      }
    }
    instanceResult.getOrThrow()
  }
}

private fun replaceScopeMarkerWithScope(args: Array<Any>, instanceScope: CoroutineScope): Boolean {
  var requiresScope = false
  for ((index, arg) in args.withIndex()) {
    if (arg === Argument.CoroutineScopeMarker) {
      requiresScope = true
      args[index] = instanceScope
    }
  }
  return requiresScope
}
