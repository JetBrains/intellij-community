fun String.publicFunction() = privateFunctionWithReceiverAndVarargs(1, 2)
fun String.publicFunction2() = privateFunctionWithReceiverAndVarargs(1, 2)

private fun String.privateFunctionWithReceiverAndVarargs(vararg fields: Int) {}

suspend fun String.publicSuspendFunction() = privateSuspendFunctionWithReceiverAndVarargs(1, 2, 3)
suspend fun String.publicSuspendFunction2() = privateSuspendFunctionWithReceiverAndVarargs(1, 2, 3)
private suspend fun String.privateSuspendFunctionWithReceiverAndVarargs(vararg fields: Int) {}

interface Fixture {}
private suspend fun Fixture.resolveRuntimeClasspath(projectPath: String = "") {}

public suspend fun main(f: Fixture) {
  f.resolveRuntimeClasspath("F")
  f.resolveRuntimeClasspath("F")
  f.resolveRuntimeClasspath("F")
  f.resolveRuntimeClasspath()
}

private suspend fun Fixture.sameAsDefault(projectPath: String = "F") {}

public suspend fun main(f: Fixture) {
  f.sameAsDefault("F")
  f.sameAsDefault("F")
  f.sameAsDefault("F")
  f.sameAsDefault()
}
