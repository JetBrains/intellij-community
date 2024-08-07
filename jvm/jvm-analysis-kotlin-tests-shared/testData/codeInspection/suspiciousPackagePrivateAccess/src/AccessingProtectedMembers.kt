package xxx

class AccessingProtectedMembersNotFromSubclass {
  fun foo() {
    val aClass: ProtectedMembers = ProtectedMembers()
    aClass.<warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>()
    ProtectedMembers.<warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>()
    <warning descr="Constructor ProtectedConstructors.ProtectedConstructors() is protected and used not through a subclass here, but declared in a different module 'dep'">ProtectedConstructors</warning>()
    <warning descr="Constructor ProtectedConstructors.ProtectedConstructors(int) is protected and used not through a subclass here, but declared in a different module 'dep'">ProtectedConstructors</warning>(1)
  }
}

@Suppress("UNUSED_VARIABLE")
class AccessingProtectedMembersFromSubclass : ProtectedMembers() {
  fun foo() {
    method()
    staticMethod()
    ProtectedMembers.staticMethod()

    val aClass = ProtectedMembers()
    aClass.<warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>()
    val myInstance = AccessingProtectedMembersFromSubclass()
    myInstance.method()

    var inner1: ProtectedMembers.StaticInner
    var inner2: StaticInner

    val runnable = object : Runnable {
      override fun run() {
        <warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>()
        <warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>()
      }
    }
  }

  private val runnable = object : Runnable {
    override fun run() {
      <warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>()
      <warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>()
    }
  }

  private val obj = object : StaticInner() {}

  private class StaticInnerImpl1 : ProtectedMembers.StaticInner()

  private class StaticInnerImpl2 : StaticInner()

  private inner class OwnInnerClass {
    fun bar() {
      <warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>()
      <warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>()
    }
  }
}

class AccessingDefaultProtectedConstructorFromSubclass : ProtectedConstructors()

class AccessingProtectedConstructorFromSubclass : ProtectedConstructors(1)

val objectAccessingDefaultProtectedConstructorFromSubclass = object : ProtectedConstructors() {}

val objectAccessingProtectedConstructorFromSubclass = object : ProtectedConstructors(1) {}

class AccessingProtectedMembersFromObjectLiteral {
  fun bar1() {
    object : ProtectedMembers() {
      fun bar2() {
        method()
        object : Runnable {
          override fun run() {
            <warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>()
          }
        }
      }
    }
  }
}

//KT-35296: Must not produce false positive warnings for package-private empty constructor.
class AccessProtectedSuperConstructor : PackagePrivateEmptyConstructor {
  constructor(i: Int) : super(i)

  constructor(i: Int, i2: Int): this(i + i2)
}

class AccessProtectedSuperConstructorWithArgument(i: Int) : PackagePrivateEmptyConstructor(i) {
  constructor(i: Int, i2: Int): this(i + i2)
}