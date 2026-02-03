package yyy;

import xxx.ProtectedMembers;

class AccessingProtectedMembersFromSubclass extends ProtectedMembers {
  void foo() {
    method();
    staticMethod();
    ProtectedMembers.staticMethod();

    ProtectedMembers.StaticInner inner1;
    StaticInner inner2;

    new Runnable() {
      public void run() {
        method();
        staticMethod();
      }
    };

    class LocalClass {
      void baz() {
        method();
        staticMethod();
      }
    }
  }

  public static class StaticInnerImpl1 extends ProtectedMembers.StaticInner {
  }

  public static class StaticInnerImpl2 extends StaticInner {
  }

  public class OwnInner {
    void bar() {
      method();
      staticMethod();
    }
  }

  public static class OwnStaticInner {
    void bar() {
      staticMethod();
    }
  }
}