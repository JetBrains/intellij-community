import org.jetbrains.annotations.*;

class InspectionTest {

  enum Type {
    PUBLIC, PRIVATE
  }

  @Nullable
  public static String foo(Type type) {
    Object obj = null;
    if (type == Type.PUBLIC) {
      obj = new Object();
    }

    switch (type) {
      case PUBLIC:
        return test(obj);
      case PRIVATE:
      default:
        return null;
    }
  }

  public static String test(@NotNull Object a) {
    return a.toString();
  }

  enum X {A, B, C}

  void testTernary(@Nullable String foo, X x) {
    switch (foo == null ? X.A : x) {
      case A:
        System.out.println(foo.<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
        break;
      case B:
        System.out.println(foo.trim());
        break;
      case C:
        System.out.println(foo.trim());
        break;
    }
  }

  void testLastUnreachable(X x) {
    if (x == X.A) {
      System.out.println("It's a");
    } else {
      switch (x) {
        case B:
          System.out.println("It's b");break;
        case C:
          System.out.println("It's c");break;
        case <warning descr="Switch label 'A' is unreachable">A</warning>:
          System.out.println("It's a again");break;
      }
    }
  }
}