fun main() {
  bar(4, "2")
}

fun bar(
  i: Int,
  s: String // todo wrongly marked as unused
) {
  println("Foo $i")
  println(s::class.java)
}