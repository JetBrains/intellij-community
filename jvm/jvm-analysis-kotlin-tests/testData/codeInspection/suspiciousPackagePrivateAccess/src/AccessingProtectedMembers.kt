package xxx

class AccessingProtectedMembersNotFromSubclass {
  fun foo() {
    val aClass: ProtectedMembers = ProtectedMembers()
    aClass.<warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method()</warning>
    ProtectedMembers.<warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod()</warning>
    <warning descr="Constructor ProtectedConstructors.ProtectedConstructors() is protected and used not through a subclass here, but declared in a different module 'dep'">ProtectedConstructors()</warning>
    <warning descr="Constructor ProtectedConstructors.ProtectedConstructors(int) is protected and used not through a subclass here, but declared in a different module 'dep'">ProtectedConstructors(1)</warning>
  }
}

@Suppress("UNUSED_VARIABLE")
class AccessingProtectedMembersFromSubclass : ProtectedMembers() {
  fun foo() {
    method()
    staticMethod()
    ProtectedMembers.staticMethod()

    val aClass = ProtectedMembers()
    aClass.<warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method()</warning>
    val myInstance = AccessingProtectedMembersFromSubclass()
    myInstance.method()

    var inner1: ProtectedMembers.StaticInner
    var inner2: StaticInner
  }

  private class StaticInnerImpl1 : ProtectedMembers.StaticInner()

  private class StaticInnerImpl2 : StaticInner()
}

class AccessingDefaultProtectedConstructorFromSubclass : ProtectedConstructors()

class AccessingProtectedConstructorFromSubclass : ProtectedConstructors(1)