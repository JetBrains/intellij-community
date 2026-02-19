public class RedundantLengthCheck {
  void f(String[] array) {
    for (int i = 0; i < 10; i++) {
      if (array == null || <warning descr="Redundant array length check">array.length == 0</warning>) {
        continue;
      }
      for (String str : array) {
        System.out.println(str);
      }
    }
  }

  void f1(String[] array) {
    if (<warning descr="Redundant array length check">array.length > 0</warning>) {
      for (String s : array) {
        System.out.println(s);
      }
    }
    if (array.length != 0) {
      for (String s : array) {
        System.out.println(s);
      }
      System.out.println("End");
    }
  }

  void f2(String[] array) {
    if (<warning descr="Redundant array length check">array.length <= 0</warning>) return;
    for (String s : array) {
      System.out.println("oops");
    }
  }

  void f2a(String[] array) {
    if (<warning descr="Redundant array length check">array.length <= 0</warning>) return;
    else {
      for (String s : array) {
        System.out.println("oops");
      }
    }
  }

  void f2b(String[] array) {
    if (Math.random() > 0.5) {
      if (<warning descr="Redundant array length check">array.length <= 0</warning>) return;
      for (String s : array) {
        System.out.println("oops");
      }
      return;
    }
    System.out.println("hello");
  }

  void f2c(String[] array) {
    if (Math.random() > 0.5) {
      if (array.length <= 0) return;
      for (String s : array) {
        System.out.println("oops");
      }
    }
    System.out.println("hello");
  }

  void f3(String[] array) {
    if (array.length <= 0) return;
    for (String s : array) {
      System.out.println("oops");
    }
    System.out.println("End");
  }

  int f4(String[] array) {
    if (<warning descr="Redundant array length check">array.length == 0</warning>) return -1;
    for (String s : array) {
      System.out.println(s);
    }
    return -1;
  }

  int f5(String[] array) {
    if (array.length == 0) return -1;
    for (String s : array) {
      System.out.println(s);
    }
    return -2;
  }

  int f6(String[] array) {
    if (<warning descr="Redundant array length check">array.length == 0</warning>) return -1;
    else {
      for (String s : array) {
        System.out.println(s);
      }
    }
    return -1;
  }

  int f7(String[] array) {
    if (Math.random() > 0.5) {
      if (<warning descr="Redundant array length check">array.length == 0</warning>) return -1;
      for (String s : array) {
        System.out.println(s);
      }
    }
    return -1;
  }

  void testSwitch(int x, int[] data) {
    int a = switch (x) {
      case 1 -> {
        if (<warning descr="Redundant array length check">data.length == 0</warning>) {
          yield 10;
        }
        for (int datum : data) {
          System.out.println(datum);
        }
        yield 10;
      }
      case 2 -> {
        if (data.length == 0) {
          yield 10;
        }
        for (int datum : data) {
          System.out.println(datum);
        }
        yield 11;
      }
      default -> 5;
    };
  }

}