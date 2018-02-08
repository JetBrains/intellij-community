import java.util.Objects;

class Test {
  private static void testMethod(Object o, Object o2, Object o3, Object o4, int i) {
    Objects.requireNonNull(o2, "no usages with null literal argument");
    if (i > 2016) {
      Objects.requireNonNull(o, "o is not null");
    }

    System.out.println(o);
    for (int j = 0; j < i; j++) {
      System.out.println(j);
      Objects.requireNonNull(o4);
    }

    if (o4 == null) {
      System.out.println("o4 is null");
    } else {
      Objects.requireNonNull(o4);
    }
  }

  public static void main(String[] args) {
    testMethod(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>, "I am not null", <warning descr="Passing 'null' argument to non-annotated parameter">null</warning>, <warning descr="Passing 'null' argument to non-annotated parameter">null</warning>, 10);
  }
}
