class Foo {
  fun f1() {
    f4()
  }

  companion object {
    fun f2(): Foo = Foo()

    fun f3(): Bar = Bar()

    fun f4() {}
  }
}

class Bar {
  fun f5() {
    Foo.f4()
  }
}
