package xxx

@Suppress("UNUSED_VARIABLE")
class AccessingProtectedKotlinMembersFromObjectLiteral {
  fun bar1() {
    object : ProtectedMembersKotlin() {
      fun bar2() {
        object : Runnable {
          override fun run() {
            foo()
            val s2 = property
          }
        }
      }
    }
  }
}

@Suppress("UNUSED_VARIABLE")
class AccessingProtectedMembersFromKotlin : ProtectedMembersKotlin() {
  fun bar() {
    foo()
    val s = property
    object : Runnable {
      override fun run() {
        foo()
        val s2 = property
      }
    }
  }
}