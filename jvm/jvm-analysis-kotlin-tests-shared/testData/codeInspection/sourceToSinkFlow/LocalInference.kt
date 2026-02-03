import org.checkerframework.checker.tainting.qual.Tainted
import org.checkerframework.checker.tainting.qual.Untainted

open class LocalInference {
  fun simpleInit() {
    val s1 = source()
    val s = s1
    sink(<warning descr="Unsafe string is used as safe parameter">s</warning>)
  }

  fun concatInit() {
    val s1 = "foo" + source() + "bar"
    val s = "foo" + s1 + "bar"
    sink(<warning descr="Unsafe string is used as safe parameter">s</warning>)
  }

  fun recursive() {
    var s1 = source()
    val s = s1
    <warning descr="[UNUSED_VALUE] The value 's' assigned to 'var s1: String defined in LocalInference.recursive' is never used">s1 =</warning> s
    sink(<warning descr="Unsafe string is used as safe parameter">s</warning>)
  }

  fun blocks() {
    var s1: String?
    run {
        s1 = source()
        run {
            val s = s1
            sink(<warning descr="Unsafe string is used as safe parameter">s</warning>)
        }
    }
  }

  fun transitive() {
    val s2: String = source() + source()
    val s1 = s2 + safe()
    val s = s1
    sink(<warning descr="Unsafe string is used as safe parameter">s</warning>)
  }

  fun transitiveRecursive(b: Boolean) {
    var s = ""
    val s1 = s
    val s2 = s1 + foo()
    val s3 = s2
    if (b) s = s3
    sink(<warning descr="Unknown string is used as safe parameter">s</warning>)
  }

  open fun foo(): String {
    return ""
  }

  fun safe(): @Untainted String {
    return "safe"
  }

  @Tainted
  fun source(): String {
    return "tainted"
  }

  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 's1' is never used">s1</warning>: @Untainted String?) {}

  inline fun <T, R> T.run(block: T.() -> R): R {
    return block()
  }
}