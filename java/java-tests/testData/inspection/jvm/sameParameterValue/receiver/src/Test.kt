fun String.publicFunction() = privateFunctionWithReceiverAndVarargs(1, 2)
fun String.publicFunction2() = privateFunctionWithReceiverAndVarargs(1, 2)

private fun String.privateFunctionWithReceiverAndVarargs(vararg fields: Int) {}

suspend fun String.publicSuspendFunction() = privateSuspendFunctionWithReceiverAndVarargs(1, 2, 3)
suspend fun String.publicSuspendFunction2() = privateSuspendFunctionWithReceiverAndVarargs(1, 2, 3)
private suspend fun String.privateSuspendFunctionWithReceiverAndVarargs(vararg fields: Int) {}
