interface Unused {
  boolean test();
}

class Test {
  Unused lambda1 = () -> {
    int i1 = 1;
    Unused lambda2 = () -> {
      int i2 = 1;
      Unused lambda3 = () -> {
        System.out.println(i2);
        int i3 = 1;
        return true;
      }
      System.out.println(lambda3);
      return false;
    };
    System.out.println(lambda2);
    return false;
  };

  public static void main(String[] args) {
    bar(() -> {
      int i1 = 1;
      Unused lambda2 = () -> {
        int i2 = 1;
        Unused lambda3 = () -> {
          System.out.println(i2);
          int i3 = 1;
          return true;
        }
        System.out.println(lambda3);
        return false;
      };
      System.out.println(lambda2);
      return false;
    });
    java.util.function.IntSupplier r = () ->
      switch (args[0]) {
        default -> {
          String s = "";
          String t;
          String u = "";
          System.out.println(u);
          yield 1;
        }
      };
  }

  static void bar(Unused unused) {
    unused.test();
  }
}
