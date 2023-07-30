// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.jcef.JBCefJSQuery.create
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asCompletableFuture
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

private typealias JsExpression = String
typealias JsExpressionResult = String?
private typealias JsExpressionResultDeferred = CompletableDeferred<JsExpressionResult>

class JBCefJsCallExecutionError(message: String) : IllegalStateException(
  "Error occurred during execution of JavaScript expression:\n $message")

/**
 * ### Obsolescence notice
 * Please use [callJavaScriptExpression] instead
 *
 * Asynchronously runs JavaScript code in the JCEF browser.
 *
 * @param javaScriptExpression
 * The passed JavaScript code should be either:
 * * a valid single-line JavaScript expression
 * * a valid multi-line function-body with at least one "return" statement
 *
 * Examples:
 * ```Kotlin
 *  browser.executeJavaScriptAsync("2 + 2")
 *     .onSuccess { r -> r /* r is 4 */ }
 *
 *  browser.executeJavaScriptAsync("return 2 + 2")
 *     .onSuccess { r -> r /* r is 4 */ }
 *
 *  browser.executeJavaScriptAsync("""
 *        function sum(s1, s2) {
 *            return s1 + s2;
 *        };
 *
 *        return sum(2,2);
 *  """.trimIndent())
 *     .onSuccess { r -> r /* r is 4 */ }
 *
 * ```
 *
 * @return The [Promise] that provides JS execution result or an error.
 */
@ApiStatus.Obsolete
fun JBCefBrowser.executeJavaScriptAsync(@Language("JavaScript") javaScriptExpression: JsExpression): Promise<JsExpressionResult> {
  return JBCefBrowserJsCall(javaScriptExpression, this)()
}

/**
 * ### Obsolescence notice
 * Please use [callJavaScriptExpression] instead
 *
 * Encapsulates the [javaScriptExpression] which is executed in the provided [browser] by the [invoke] method.
 * Handles JavaScript errors and submits them to the execution result.
 * @param javaScriptExpression
 * The passed JavaScript code should be either:
 * * a valid single-line JavaScript expression
 * * a valid multi-line function-body with at least one "return" statement
 *
 * @see [executeJavaScriptAsync]
 */
@ApiStatus.Obsolete
class JBCefBrowserJsCall(@Language("JavaScript") private val javaScriptExpression: JsExpression, private val browser: JBCefBrowser) {

  // TODO: Ensure the related JBCefClient has a sufficient number of slots in the pool

  /**
   * Performs [javaScriptExpression] in the JCEF [browser] asynchronously.
   * @return The [Promise] that provides JS execution result or an error.
   * @throws IllegalStateException if the related [browser] is not initialized (displayed).
   * @throws IllegalStateException if the related [browser] is disposed.
   * @see [com.intellij.ui.jcef.JBCefBrowserBase.isCefBrowserCreated]
   */
  operator fun invoke(): Promise<JsExpressionResult> {
    return browser.callJavaScriptExpressionImpl(javaScriptExpression).asCompletableFuture().asPromise()
  }

}

/**
 * Asynchronously runs JavaScript code in the JCEF browser.
 *
 * See [Kotlin coroutines](https://youtrack.jetbrains.com/articles/IJPL-A-3/Kotlin-Coroutines)
 *
 * @param expression
 * The passed JavaScript code should be either:
 * * a valid single-line JavaScript expression
 * * a valid multi-line function-body with at least one "return" statement
 *
 *
 * Examples:
 * ```Kotlin
 *  browser.callJavaScriptExpression("2 + 2")
 *  .let { r -> r /* r is 4 */ }
 *
 *  browser.callJavaScriptExpression("return 2 + 2")
 *  .let { r -> r /* r is 4 */ }
 *
 *  browser.callJavaScriptExpression("""
 *        function sum(s1, s2) {
 *            return s1 + s2;
 *        };
 *
 *        return sum(2,2);
 *  """.trimIndent())
 *     ..let { r -> r /* r is 4 */ }
 *
 * ```
 *
 * @return The [Deferred] that provides JS execution result or an error.
 * @throws IllegalStateException if the related [this] is not initialized (displayed).
 * @throws IllegalStateException if the related [this] is disposed.
 * @throws JBCefJsCallExecutionError if the execution of [expression] failed inside the browser
 **/
suspend fun JBCefBrowser.callJavaScriptExpression(@Language("JavaScript") expression: JsExpression): JsExpressionResult =
  callJavaScriptExpressionImpl(expression).await()

private fun JBCefBrowser.callJavaScriptExpressionImpl(@Language("JavaScript") expression: JsExpression): Deferred<JsExpressionResult> {
  if (isCefBrowserCreated.not())
    throw IllegalStateException("Failed to execute the requested JS expression. The related JCEF browser in not initialized.")

  /**
   * The root [com.intellij.openapi.Disposable] object that indicates the lifetime of this call.
   * Remains undisposed until the [expression] gets executed in [this] browser (either successfully or with an error).
   */
  val executionLifetime: CheckedDisposable = Disposer.newCheckedDisposable().also { Disposer.register(this, it) }

  if (executionLifetime.isDisposed)
    throw IllegalStateException(
      "Failed to execute the requested JS expression. The related browser is disposed.")

  val resultDeferred: JsExpressionResultDeferred = CompletableDeferred<JsExpressionResult>().apply {
    invokeOnCompletion {
      Disposer.dispose(executionLifetime)
    }
  }

  Disposer.register(executionLifetime) {
    resultDeferred.completeExceptionally(IllegalStateException("The related browser is disposed during the call."))
  }

  val resultHandlerQuery: JBCefJSQuery = createResultHandlerQuery(executionLifetime, resultDeferred)
  val errorHandlerQuery: JBCefJSQuery = createErrorHandlerQuery(executionLifetime, resultDeferred)

  val jsToRun = expression.wrapWithErrorHandling(resultQuery = resultHandlerQuery, errorQuery = errorHandlerQuery)

  try {
    cefBrowser.executeJavaScript(jsToRun, "", 0)
  }
  catch (ex: Exception) {
    // In case something goes wrong with the browser interop
    resultDeferred.completeExceptionally(ex)
  }

  return resultDeferred
}

private fun JBCefBrowser.createResultHandlerQuery(parentDisposable: Disposable, resultPromise: JsExpressionResultDeferred) =
  createQuery(parentDisposable).apply {
    addHandler { result ->
      resultPromise.complete(result)
      null
    }
  }

private fun JBCefBrowser.createErrorHandlerQuery(parentDisposable: Disposable, resultPromise: JsExpressionResultDeferred) =
  createQuery(parentDisposable).apply {
    addHandler { errorMessage ->
      resultPromise.completeExceptionally(
        JBCefJsCallExecutionError(errorMessage ?: "Unknown error")
      )
      null
    }
  }

private fun JBCefBrowser.createQuery(parentDisposable: Disposable): JBCefJSQuery {
  return create(this as JBCefBrowserBase).also { Disposer.register(parentDisposable, it) }
}

private fun JsExpression.asFunctionBody(): JsExpression = let { expression ->
  when {
    StringUtil.containsLineBreak(expression) -> expression
    StringUtil.startsWith(expression, "return") -> expression
    else -> "return $expression"
  }
}

@Suppress("JSVoidFunctionReturnValueUsed")
@Language("JavaScript")
private fun @receiver:Language("JavaScript") JsExpression.wrapWithErrorHandling(resultQuery: JBCefJSQuery, errorQuery: JBCefJSQuery) = """
      function payload() {
          ${asFunctionBody()}
      }

      try {
          let result = payload();

          // call back the related JBCefJSQuery
          window.${resultQuery.funcName}({
              request: "" + result,
              onSuccess: function (response) {
                  // do nothing
              }, onFailure: function (error_code, error_message) {
                  // do nothing
              }
          });
      } catch (e) {
          // call back the related error handling JBCefJSQuery
          window.${errorQuery.funcName}({
              request: "" + e,
              onSuccess: function (response) {
                  // do nothing
              }, onFailure: function (error_code, error_message) {
                  // do nothing
              }
          });
      }
    """.trimIndent()