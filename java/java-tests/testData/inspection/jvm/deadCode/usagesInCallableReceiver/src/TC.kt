fun main() {
  foo(1, "")
}

fun foo(i: Int,
        s: String //todo wrongly marked as unused, because simple references in callables are treated as class references
) {
  println("Foo $i"::toString)
  println(s::toString)
}