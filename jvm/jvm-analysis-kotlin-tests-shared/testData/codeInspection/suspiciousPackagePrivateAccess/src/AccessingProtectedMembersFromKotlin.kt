package xxx

@Suppress("UNUSED_VARIABLE")
class AccessingProtectedKotlinMembersFromObjectLiteral {
  fun bar1() {
    object : ProtectedMembersKotlin() {
      fun bar2() {
        object : Runnable {
          override fun run() {
            <warning descr="Function ProtectedMembersKotlin.foo() is protected and used not through a subclass here, but declared in a different module 'dep'">foo</warning>()
            val s2 = <warning descr="Property ProtectedMembersKotlin.property is protected and used not through a subclass here, but declared in a different module 'dep'">property</warning>
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
        <warning descr="Function ProtectedMembersKotlin.foo() is protected and used not through a subclass here, but declared in a different module 'dep'">foo</warning>()
        val s2 = <warning descr="Property ProtectedMembersKotlin.property is protected and used not through a subclass here, but declared in a different module 'dep'">property</warning>
      }
    }
  }
}