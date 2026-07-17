class Outer {
  fun self(): Outer = create()

  companion object {
    fun create(): Outer = Outer()
  }
}

fun main() {
  println(Outer().self())
}
