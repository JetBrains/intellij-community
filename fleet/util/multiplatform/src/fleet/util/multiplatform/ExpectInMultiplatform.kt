package fleet.util.multiplatform


/**
 * During compilation, calls of this function are linked to another function if:
 * - This is the only operation of the host function (`fun a(): Any = linkToActual()`, `fun b() { return linkToActual() }`)
 * - There is a function marked with `@Actual` that has the same signature as the host function except name that matches the annotation.
 *
 * This should be used as a replacement for expect/actual in places this is not available (JPS based projects), and relies on
 * dedicated compiler plugin to work.
 *
 * One `@Actual` should be defined in every target we're trying to compile.
 *
 * @see Actual
 */
fun <T> linkToActual(): T = error(
  "this function wasn't linked to actual function, please add 'expects' compiler plugin to the source module or make this the only call of the enclosing function"
)

/**
 * Provide an actual declaration for a function calling `linkToActual()`.
 *
 * Functions marked with this annotation should ideally not be called and be made internal, since they could get replaced by
 * 'actual' implementation when Kotlin Multiplatform support becomes available.
 *
 * @param linkedTo name of the host function we're implementing
 * @see linkToActual
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Actual(val linkedTo: String)
