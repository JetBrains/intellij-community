fun main() {
  foo(1, "")
}

fun foo(i: Int,
        s: String
) {
  println("Foo $i"::toString)
  println(s::toString)
}