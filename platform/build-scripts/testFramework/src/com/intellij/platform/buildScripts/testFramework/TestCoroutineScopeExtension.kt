// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * JUnit 5 extension that provides a managed [CoroutineScope] for `@TestFactory` methods.
 *
 * **Important:** This extension is intended ONLY for `@TestFactory` methods that return dynamic tests
 * which need a shared scope. For regular `@Test` methods, use `runBlocking(Dispatchers.Default)` instead.
 *
 * Usage:
 * ```
 * @ExtendWith(TestCoroutineScopeExtension::class)
 * class MyTest {
 *   @TestFactory
 *   fun test(scope: CoroutineScope): Iterator<DynamicTest> {
 *     // use scope for async operations across dynamic tests
 *   }
 * }
 * ```
 *
 * The scope is created before each test method and canceled after that method completes.
 * For `@TestFactory`, this includes all dynamic tests produced by the factory.
 */
class TestCoroutineScopeExtension : BeforeEachCallback, AfterEachCallback, ParameterResolver {
  private companion object {
    private val NAMESPACE = ExtensionContext.Namespace.create(TestCoroutineScopeExtension::class.java)
    private const val SCOPE_KEY = "coroutineScope"
  }

  override fun beforeEach(context: ExtensionContext) {
    @Suppress("RAW_SCOPE_CREATION")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName(context.displayName))
    context.getStore(NAMESPACE).put(SCOPE_KEY, scope)
  }

  override fun afterEach(context: ExtensionContext) {
    (context.getStore(NAMESPACE).get(SCOPE_KEY) as? CoroutineScope)?.cancel()
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return parameterContext.parameter.type == CoroutineScope::class.java
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    return extensionContext.getStore(NAMESPACE).get(SCOPE_KEY) as CoroutineScope
  }
}
