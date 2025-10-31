import org.intellij.lang.annotations.Language
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.*

/**
 * Control flow exceptions should be rethrown rather than just logged
 */
@Language("HTML")
val htmlDescription = """
    <html>
    <body>
        Control flow exceptions (like interrupts, cancellations, etc.) should be rethrown rather than just logged. 
        These exceptions are used for flow control and need to propagate up the call stack. 
        Catching and only logging them can lead to unexpected behavior, as they're silently swallowed 
        instead of properly handled by higher levels in the application.
    </body>
    </html>
""".trimIndent()

val controlFlowExceptionsShouldBeRethrownInspection = localInspection { psiFile, inspection ->
    fun isControlFlowException(exceptionType: String): Boolean {
        return exceptionType.contains("InterruptedException") ||
               exceptionType.contains("CancellationException") ||
               exceptionType.contains("InterruptedIOException") ||
               exceptionType.contains("ClosedByInterruptException")
    }
    
    fun isLoggingCall(expression: KtExpression): Boolean {
        val callExpression = expression as? KtCallExpression ?: return false
        val callee = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
        val methodName = callee.getReferencedName()
        return methodName in setOf("error", "warn", "info", "debug", "trace", "log")
    }
    
    fun hasRethrowCheck(catchClause: KtCatchClause): Boolean {
        val blockExpression = catchClause.catchBody as? KtBlockExpression ?: return false
        return blockExpression.statements.any { statement ->
            when (statement) {
                is KtThrowExpression -> true
                is KtIfExpression -> {
                    val thenBranch = statement.then as? KtBlockExpression
                    thenBranch?.statements?.any { it is KtThrowExpression } == true
                }
                else -> false
            }
        }
    }
    
    val tryExpressions = psiFile.descendantsOfType<KtTryExpression>()
    
    tryExpressions.forEach { tryExpression ->
        tryExpression.catchClauses.forEach { catchClause ->
            val parameter = catchClause.catchParameter ?: return@forEach
            val parameterType = parameter.typeReference?.text ?: return@forEach
            
            if (isControlFlowException(parameterType)) {
                val catchBody = catchClause.catchBody as? KtBlockExpression ?: return@forEach
                val hasLogging = catchBody.statements.any { statement ->
                    statement.descendantsOfType<KtCallExpression>().any { call ->
                        isLoggingCall(call)
                    }
                }
                
                if (hasLogging && !hasRethrowCheck(catchClause)) {
                    inspection.registerProblem(
                        catchClause,
                        "Control flow exception '${parameterType}' should be rethrown rather than just logged"
                    )
                }
            }
        }
    }
}

listOf(
    InspectionKts(
        id = "ControlFlowExceptionsShouldBeRethrown",
        localTool = controlFlowExceptionsShouldBeRethrownInspection,
        name = "Control flow exceptions should be rethrown",
        htmlDescription = htmlDescription,
        level = HighlightDisplayLevel.WARNING,
    )
)