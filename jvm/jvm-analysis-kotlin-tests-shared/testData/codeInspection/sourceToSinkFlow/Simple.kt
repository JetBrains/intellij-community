import org.checkerframework.checker.tainting.qual.Tainted
import org.checkerframework.checker.tainting.qual.Untainted

class Simple {
  fun simple() {
    val s = source()
    sink(<warning descr="Unsafe string is used as safe parameter">s</warning>)
  }

  fun alias() {
    val s1 = source()
    sink(<warning descr="Unsafe string is used as safe parameter">s1</warning>)
  }

  fun unknown() {
    val s = foo()
    sink(s)
  }

  fun unknown2(v: String) {
    val s = foo2(v)
    sink(<warning descr="Unknown string is used as safe parameter">s</warning>)
  }

  fun literalOnly() {
    var s: String? = <warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 's' initializer is redundant">null</warning>
    s = "safe"
    sink(s)
  }

  fun safeCall() {
    var s: String? = <warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 's' initializer is redundant">"safe"</warning>
    s = safe()
    sink(s)
  }

  fun sourceCallToSink() {
    sink(<warning descr="Unsafe string is used as safe parameter">source()</warning>)
  }

  fun safeCallToSink() {
    sink(safe())
  }

  fun sourceFromClass() {
    val s = WithSourceParent().source()
    sink(<warning descr="Unsafe string is used as safe parameter">s</warning>)
  }

  fun sourceFromChildClass() {
    val child = WithSourceChild()
    val s = child.source()
    sink(<warning descr="Unsafe string is used as safe parameter">s</warning>)
  }

  fun withParenthesis() {
    var s1 = <warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 's1' initializer is redundant">(source())</warning>
    s1 = (foo())
    val s = (s1)
    sink((s))
  }

  fun unsafeReturn(): @Untainted String? {
    return <warning descr="Unsafe string is returned from safe method">source()</warning>
  }

  fun unsafeConcat() {
    @Tainted val s = source()
    val s1 = "safe"
    val s2 = "safe2"
    sink(<warning descr="Unsafe string is used as safe parameter">s1 + s + s2</warning>)
  }

  fun unsafeTernary(b: Boolean) {
    @Tainted val s = source()
    sink(<warning descr="Unsafe string is used as safe parameter">if (b) s else null</warning>)
  }

  fun unknownIfExpression(b: Boolean): @Untainted String {
    var s: String = if (b) bar() else foo()
    return s
  }

  fun unknownIfExpression(b: Boolean, v: String): @Untainted String {
    var s: String = if (b) bar() else foo2(v)
    return <warning descr="Unknown string is returned from safe method">s</warning>
  }

  fun callSource(): String {
    return source()
  }

  fun foo(): String {
    return "some"
  }

  fun foo2(v: String): String {
    return v
  }

  fun bar(): String {
    return "other"
  }

  fun safe(): @Untainted String? {
    return "safe"
  }

  @Tainted
  fun source(): String {
    return "tainted"
  }

  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 's1' is never used">s1</warning>: @Untainted String?) {}
  internal open inner class WithSourceParent {
    @Tainted
    open fun source(): String {
      return "tainted"
    }
  }

  internal inner class WithSourceChild : WithSourceParent() {
    override fun source(): String {
      return super.source()
    }
  }
}