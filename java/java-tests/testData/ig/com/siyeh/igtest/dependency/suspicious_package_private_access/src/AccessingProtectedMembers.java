package xxx;

class AccessingProtectedMembersNotFromSubclass {
  void foo() {
    ProtectedMembers aClass = new ProtectedMembers();
    aClass.<warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>();
    ProtectedMembers.<warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>();
    new <warning descr="Constructor ProtectedConstructors.ProtectedConstructors() is protected and used not through a subclass here, but declared in a different module 'dep'">ProtectedConstructors</warning>();
    new <warning descr="Constructor ProtectedConstructors.ProtectedConstructors(int) is protected and used not through a subclass here, but declared in a different module 'dep'">ProtectedConstructors</warning>(1);
    new ProtectedConstructors() {};
    new ProtectedConstructors(1) {};
  }

  void baz() {
    class LocalSubclass extends ProtectedMembers {
      void bar() {
        method();
        staticMethod();
      }
    }
  }
}

class AccessingProtectedMembersFromSubclass extends ProtectedMembers {
  void foo() {
    method();
    staticMethod();
    ProtectedMembers.staticMethod();

    ProtectedMembers aClass = new ProtectedMembers();
    aClass.<warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>();
    AccessingProtectedMembersFromSubclass myInstance = new AccessingProtectedMembersFromSubclass();
    myInstance.method();

    ProtectedMembers.StaticInner inner1;
    StaticInner inner2;

    new Runnable() {
      public void run() {
        <warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>();
        <warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>();
      }
    };

    class LocalClass {
      void baz() {
        <warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>();
        <warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>();
      }
    }

    new StaticInner() {
      protected void protectedMethod() {
        super.protectedMethod();
      }
    };

    new BaseClassWithArg(method()) {
    };

    new BaseClassWithArg(field) {
    };
  }

  public static class StaticInnerImpl1 extends ProtectedMembers.StaticInner {
  }

  public static class StaticInnerImpl2 extends StaticInner {
  }

  public static class StaticInnerImpl3 extends ProtectedMembers.StaticInner {
    // No warning must be generated here: "Access to protected ProtectedMembers.StaticInner declared in a different module
    public StaticInnerImpl3() {
      super();
    }

    public StaticInnerImpl3(int x) {
      super(x);
    }
  }

  public class OwnInner {
    void bar() {
      <warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>();
      <warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>();
    }
  }

  public static class OwnStaticInner {
    void bar() {
      <warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>();
    }
  }

  private abstract static class BaseClassWithArg {
    public BaseClassWithArg(String arg) {

    }
  }
}

class AccessingDefaultProtectedConstructorFromSubclass extends ProtectedConstructors {
}

class AccessingProtectedConstructorFromSubclass extends ProtectedConstructors {
  AccessingProtectedConstructorFromSubclass() {
    super(1);
  }
}

//KT-35296: Must not produce false positive warnings for package-private empty constructor.
class AccessProtectedSuperConstructorInsteadOfEmptyPackagePrivate extends PackagePrivateEmptyConstructor {
  AccessProtectedSuperConstructorInsteadOfEmptyPackagePrivate(int i) {
    super(i);
  }

  AccessProtectedSuperConstructorInsteadOfEmptyPackagePrivate(int i, int i2) {
    this(i + i2);
  }
}