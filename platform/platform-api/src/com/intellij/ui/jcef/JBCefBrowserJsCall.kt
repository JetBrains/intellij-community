// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

typealias JsExpression = String
typealias JsExpressionResult = String?
typealias JsExpressionResultPromise = AsyncPromise<JsExpressionResult>

/**
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
fun JBCefBrowser.executeJavaScriptAsync(@Language("JavaScript") javaScriptExpression: JsExpression): Promise<JsExpressionResult> =
  JBCefBrowserJsCall(javaScriptExpression, this)()


/**
 * Encapsulates the [javaScriptExpression] which is executed in the provided [browser] by the [invoke] method.
 * Handles JavaScript errors and submits them to the execution result.
 * @param javaScriptExpression
 * The passed JavaScript code should be either:
 * * a valid single-line JavaScript expression
 * * a valid multi-line function-body with at least one "return" statement
 *
 * @see [executeJavaScriptAsync]
 */
class JBCefBrowserJsCall(val javaScriptExpression: JsExpression, val browser: JBCefBrowser) {

  // TODO: Ensure the related JBCefClient has a sufficient number of slots in the pool

  /**
   * Performs [javaScriptExpression] in the JCEF [browser] asynchronously.
   * @return The [Promise] that provides JS execution result or an error.
   * @throws IllegalStateException if the related [browser] is not initialized (displayed).
   * @throws IllegalStateException if the related [browser] is disposed.
   * @see [com.intellij.ui.jcef.JBCefBrowserBase.isCefBrowserCreated]
   */
  operator fun invoke(): Promise<JsExpressionResult> {
    if (browser.isCefBrowserCreated.not())
      throw IllegalStateException("Failed to execute the requested JS expression. The related JCEF browser in not initialized.")

    /**
     * The root [com.intellij.openapi.Disposable] object that indicates the lifetime of this call.
     * Remains undisposed until the [javaScriptExpression] gets executed in the [browser] (either successfully or with an error).
     */
    val executionLifetime: CheckedDisposable = Disposer.newCheckedDisposable().also { Disposer.register(browser, it) }

    if (executionLifetime.isDisposed)
      throw IllegalStateException(
        "Failed to execute the requested JS expression. The related browser is disposed.")

    val resultPromise = JsExpressionResultPromise().apply {
      onProcessed {
        Disposer.dispose(executionLifetime)
      }
    }

    Disposer.register(executionLifetime) {
      resultPromise.setError("The related browser is disposed during the call.")
    }

    val resultHandlerQuery: JBCefJSQuery = createResultHandlerQuery(executionLifetime, resultPromise)
    val errorHandlerQuery: JBCefJSQuery = createErrorHandlerQuery(executionLifetime, resultPromise)

    val jsToRun = javaScriptExpression.wrapWithErrorHandling(resultQuery = resultHandlerQuery, errorQuery = errorHandlerQuery)

    try {
      browser.cefBrowser.executeJavaScript(jsToRun, "", 0)
    }
    catch (ex: Exception) {
      // In case something goes wrong with the browser interop
      resultPromise.setError(ex)
    }

    return resultPromise
  }

  private fun createResultHandlerQuery(parentDisposable: Disposable, resultPromise: JsExpressionResultPromise) =
    createQuery(parentDisposable).apply {
      addHandler { result ->
        resultPromise.setResult(result)
        null
      }
    }

  private fun createErrorHandlerQuery(parentDisposable: Disposable, resultPromise: JsExpressionResultPromise) =
    createQuery(parentDisposable).apply {
      addHandler { errorMessage ->
        resultPromise.setError(errorMessage ?: "Unknown error")
        null
      }
    }

  private fun createQuery(parentDisposable: Disposable) = JBCefJSQuery.create(browser).also { Disposer.register(parentDisposable, it) }

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
}
