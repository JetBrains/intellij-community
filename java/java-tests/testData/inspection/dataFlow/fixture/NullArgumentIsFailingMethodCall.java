import java.util.Objects;

class Test {
  private static void testMethod(Object <warning descr="Method will throw an exception when parameter is null">o</warning>, Object o2, Object <warning descr="Method will throw an exception when parameter is null">o3</warning>, Object o4, int i) {
    Objects.requireNonNull(o, "o is not null");
    if (o3 != null) {
      System.out.println(o3.hashCode());
    } else {
      throw new NullPointerException();
    }

    System.out.println(o);
    for (int j = 0; j < i; j++) {
      System.out.println(j);
      Objects.requireNonNull(o4);
    }
    Objects.requireNonNull(o3);

    if (o4 == null) {
      System.out.println("o4 is null");
    } else {
      Objects.requireNonNull(o4);
    }
  }

  public static void main(String[] args) {
    testMethod(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>, <warning descr="Passing 'null' argument to non-annotated parameter">null</warning>, <warning descr="Passing 'null' argument to non-annotated parameter">null</warning>, <warning descr="Passing 'null' argument to non-annotated parameter">null</warning>, 10);
  }
}