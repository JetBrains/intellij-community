fun main() {
  bar(4, "2")
}

fun bar(
  i: Int,
  s: String
) {
  println("Foo $i")
  println(s::class.java)
}