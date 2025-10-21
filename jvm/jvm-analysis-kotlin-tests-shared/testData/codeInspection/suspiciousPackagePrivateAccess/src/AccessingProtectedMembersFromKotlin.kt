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
            "xyz".<warning descr="Function ProtectedMembersKotlin.extensionFunction() on String is protected and used not through a subclass here, but declared in a different module 'dep'">extensionFunction</warning>()
            "xyz".<warning descr="Property ProtectedMembersKotlin.extensionProperty is protected and used not through a subclass here, but declared in a different module 'dep'">extensionProperty</warning>
            <warning descr="Function ProtectedMembersKotlin.Companion.jvmStaticFunction() is protected and used not through a subclass here, but declared in a different module 'dep'">jvmStaticFunction</warning>()
            <warning descr="Property ProtectedMembersKotlin.Companion.jvmStaticProperty is protected and used not through a subclass here, but declared in a different module 'dep'">jvmStaticProperty</warning>
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
    "xyz".extensionFunction()
    val t = "xyz".extensionProperty
    jvmStaticFunction()
    jvmStaticProperty
    object : Runnable {
      override fun run() {
        <warning descr="Function ProtectedMembersKotlin.foo() is protected and used not through a subclass here, but declared in a different module 'dep'">foo</warning>()
        val s2 = <warning descr="Property ProtectedMembersKotlin.property is protected and used not through a subclass here, but declared in a different module 'dep'">property</warning>
        "xyz".<warning descr="Function ProtectedMembersKotlin.extensionFunction() on String is protected and used not through a subclass here, but declared in a different module 'dep'">extensionFunction</warning>()
        val t2 = "xyz".<warning descr="Property ProtectedMembersKotlin.extensionProperty is protected and used not through a subclass here, but declared in a different module 'dep'">extensionProperty</warning>
        <warning descr="Function ProtectedMembersKotlin.Companion.jvmStaticFunction() is protected and used not through a subclass here, but declared in a different module 'dep'">jvmStaticFunction</warning>()
        <warning descr="Property ProtectedMembersKotlin.Companion.jvmStaticProperty is protected and used not through a subclass here, but declared in a different module 'dep'">jvmStaticProperty</warning>
      }
    }
  }
}