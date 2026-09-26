class Outer {
  fun self(): Outer = create()

  object Inner {
    fun create(): Outer = Outer()
  }
}

fun main() {
  println(Outer().self())
}
