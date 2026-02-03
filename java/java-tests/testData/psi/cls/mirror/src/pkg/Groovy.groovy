package pkg

import java.util.concurrent.Callable

class Groovy {
  static class Nested { }
  class Inner { }

  final Nested n = new Nested()
  final Inner i = new Inner()
  final Runnable r = { println("I'm runnable") }
  final Callable<String> c = { "I'm callable" }
}