class X {
  var yyy: () -> Unit = {}

  fun x() {
    yyy()
  }
}

fun main() {
  X().x()
}