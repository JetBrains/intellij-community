package fleet.util.multiplatform


/**
 * During compilation, calls of this function are linked to another function if:
 * - This is the only operation of the host function (`fun a(): Any = linkToActual()`, `fun b(i: String): String { return linkToActual() }`)
 * - There is a function marked with `@Actual` that has the same signature as the host function and the same name + a suffix depending on
 * the platform (`@Actual fun aJvm(): Any = 0`, `@Actual fun bWasmJs(i: String): String = ""`).
 *
 * The suffix is based on the Kotlin platform name (Jvm, WasmJs for instance), in builds involving several platforms at once (untested behavior!),
 * the suffix would be `Impl`.
 *
 * This should be used as a replacement for expect/actual in places where this is not available (JPS-based projects) and relies on
 *  a dedicated compiler plugin to work.
 *
 * One `@Actual` should be defined in every target we're trying to compile.
 *
 * `@Actual` and `linkToExpect()` functions should be in the same module.
 *
 * @see Actual
 */
fun <T> linkToActual(): T = error(
  "this function wasn't linked to actual function, please add 'expects' compiler plugin to the source module or make this the only call of the enclosing function"
)

/**
 * Provide an actual declaration for a function calling `linkToActual()`.
 * The function should have the same name as the expect function plus a suffix based on the platform (Jvm, WasmJs...).
 *
 * Functions marked with this annotation should ideally not be called and be made internal, since they could get replaced by
 * 'actual' implementation when Kotlin Multiplatform support becomes available.
 *
 * @param linkedTo name of the host function we're implementing
 * @see linkToActual
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Actual(
  @Deprecated("name of the function is now used instead")
  val linkedTo: String = ""
)

