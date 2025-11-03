import org.intellij.lang.annotations.Language
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.*

/**
 * Control flow exceptions should be rethrown when catching Throwable or broad exception types
 */
@Language("HTML")
val htmlDescription = """
    <html>
    <body>
        When catching Throwable or broad exception types, control flow exceptions (like InterruptedException, CancellationException) should be rethrown rather than just logged. Not rethrowing these exceptions can lead to silent failures in cancellation logic, thread interruption handling, or other control flow mechanisms.
    </body>
    </html>
""".trimIndent()

val controlFlowExceptionsShouldBeRethrownInspection = localInspection { psiFile, inspection ->
    fun isControlFlowException(typeName: String): Boolean {
        return typeName == "InterruptedException" ||
               typeName == "CancellationException" ||
               typeName.endsWith(".InterruptedException") ||
               typeName.endsWith(".CancellationException")
    }
    
    fun isBroadExceptionType(typeName: String): Boolean {
        return typeName == "Throwable" ||
               typeName == "Exception" ||
               typeName.endsWith(".Throwable") ||
               typeName.endsWith(".Exception")
    }
    
    fun hasRethrowCheck(catchBlock: KtBlockExpression, parameterName: String): Boolean {
        val statements = catchBlock.statements
        return statements.any { statement ->
            when (statement) {
                is KtIfExpression -> {
                    val condition = statement.condition
                    val thenBranch = statement.then
                    
                    // Check if condition involves checking the exception type
                    val hasTypeCheck = condition?.descendantsOfType<KtCallExpression>()?.any { call ->
                        val calleeText = call.calleeExpression?.text
                        calleeText == "shouldRethrow" || calleeText?.contains("shouldRethrow") == true
                    } ?: false
                    
                    // Check if then branch has throw statement
                    val hasThrow = thenBranch?.descendantsOfType<KtThrowExpression>()?.any { throwExpr ->
                        throwExpr.thrownExpression?.text == parameterName
                    } ?: false
                    
                    hasTypeCheck && hasThrow
                }
                is KtThrowExpression -> {
                    statement.thrownExpression?.text == parameterName
                }
                else -> false
            }
        }
    }
    
    val tryExpressions = psiFile.descendantsOfType<KtTryExpression>()
    
    tryExpressions.forEach { tryExpression ->
        val catchClauses = tryExpression.catchClauses
        
        catchClauses.forEach { catchClause ->
            val parameter = catchClause.catchParameter
            val parameterName = parameter?.name
            val parameterType = parameter?.typeReference?.text
            
            if (parameterName != null && parameterType != null && isBroadExceptionType(parameterType)) {
                val catchBlock = catchClause.catchBody as? KtBlockExpression
                
                if (catchBlock != null) {
                    val hasRethrow = hasRethrowCheck(catchBlock, parameterName)
                    
                    if (!hasRethrow) {
                        inspection.registerProblem(
                            catchClause,
                            "Control flow exceptions should be rethrown when catching $parameterType. Consider adding a check like 'if (Logger.shouldRethrow($parameterName)) throw $parameterName' before logging."
                        )
                    }
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