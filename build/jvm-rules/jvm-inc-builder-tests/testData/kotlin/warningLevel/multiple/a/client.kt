package test

class Client {
  fun useFoo(u: Util): Int = u.foo("param")
  fun useBox(u: Util): Any = u.box()
}
