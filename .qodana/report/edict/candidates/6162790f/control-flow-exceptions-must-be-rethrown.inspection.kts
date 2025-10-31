import org.intellij.lang.annotations.Language
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.*

@Language("HTML")
val htmlDescription = """
    <html>
    <body>
        Control flow exceptions should be rethrown in generic catch blocks. When catching Throwable, 
        control flow exceptions like InterruptedException and CancellationException should be rethrown 
        rather than being swallowed or just logged, as they are part of the application's control flow mechanism.
    </body>
    </html>
""".trimIndent()

val controlFlowExceptionsShouldBeRethrownInspection = localInspection { psiFile, inspection ->
    fun isThrowableType(typeReference: KtTypeReference?): Boolean {
        if (typeReference == null) return false
        val typeText = typeReference.text
        return typeText == "Throwable" || typeText.endsWith(".Throwable")
    }
    
    fun hasControlFlowExceptionCheck(catchBlock: KtBlockExpression): Boolean {
        // Look for patterns like Logger.shouldRethrow(t) or similar control flow checks
        val callExpressions = catchBlock.descendantsOfType<KtCallExpression>().toList()
        return callExpressions.any { call ->
            val calleeText = call.calleeExpression?.text
            calleeText == "shouldRethrow" || 
            (call.calleeExpression is KtDotQualifiedExpression &&
             (call.calleeExpression as KtDotQualifiedExpression).selectorExpression?.text == "shouldRethrow")
        }
    }
    
    fun hasThrowStatement(catchBlock: KtBlockExpression): Boolean {
        return catchBlock.descendantsOfType<KtThrowExpression>().toList().isNotEmpty()
    }
    
    val tryExpressions = psiFile.descendantsOfType<KtTryExpression>().toList()
    
    tryExpressions.forEach { tryExpression ->
        val catchClauses = tryExpression.catchClauses
        
        catchClauses.forEach { catchClause ->
            val parameter = catchClause.catchParameter
            val parameterType = parameter?.typeReference
            
            if (isThrowableType(parameterType)) {
                val catchBlock = catchClause.catchBody as? KtBlockExpression ?: return@forEach
                
                // Check if there's already a control flow exception check or throw statement
                if (!hasControlFlowExceptionCheck(catchBlock) && !hasThrowStatement(catchBlock)) {
                    inspection.registerProblem(
                        catchClause,
                        "Control flow exceptions should be rethrown when catching Throwable"
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
        name = "Control flow exceptions should be rethrown in generic catch blocks",
        htmlDescription = htmlDescription,
        level = HighlightDisplayLevel.WARNING,
    )
)