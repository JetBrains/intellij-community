interface I {
  int m(int i);
}
interface J {
  int m();
}

public class XXX {

    static void foo() {
        int l = 0;
        int j = 0;
        j = 2;
        final int L = 0;
        I i = (int h) -> { int k = 0; return h + <error descr="Variable used in lambda expression should be final or effectively final">j</error> + l + L; };
    }

    void bar() {
        int l = 0;
        int j = 0;
        j = 2;
        final int L = 0;
        I i = (int h) -> { int k = 0; return h + k + <error descr="Variable used in lambda expression should be final or effectively final">j</error> + l + L; };
    }
    
     void foo(J i) { }
 
     void m1(int x) {
         int y = 1;
         foo(() -> x+y);
     }
 
     void m2(int x) {
         int y;
         y = 1;
         foo(() -> x+y);
     }
 
     void m3(int x, boolean cond) {
         int y;
         if (cond) y = 1;
         foo(() -> x+<error descr="Variable 'y' might not have been initialized">y</error>);
     }
 
     void m4(int x, boolean cond) {
         int y;
         if (cond) y = 1;
         else y = 2;
         foo(() -> x+y);
     }
 
     void m5(int x, boolean cond) {
         int y;
         if (cond) y = 1;
         y = 2;
         foo(() -> x+<error descr="Variable used in lambda expression should be final or effectively final">y</error>);
     }
 
     void m6(int x) {
         foo(() -> <error descr="Variable used in lambda expression should be final or effectively final">x</error>+1);
       x++;
     }
 
     void m7(int x) {
         foo(() -> <error descr="Variable used in lambda expression should be final or effectively final">x</error>=1);
     }
 
     void m8() {
         int y;
         foo(() -> <error descr="Variable used in lambda expression should be final or effectively final">y</error>=1);
     }
     
     void m9() {
         int x = 42;
         for (int i = 0; i < 2; i++) {
           x = x + 42;
         }
         int y = 5;
         foo(() -> y);
     }
}

class Sample {
        public static void main(String[] args) {
                Runnable runnable = () -> {
                        Integer i;
                        if (true) {
                                i = 111;
                                System.out.println(i);
                        }
                };

                Runnable runnable2 = () -> {
                        Integer i2 = 333;
                        i2 = 444;
                        System.out.println(i2);
                };

                runnable.run();         // prints 111
                runnable2.run();        // prints 444
        }
}

class ParameterIsEffectivelyFinal {
  {
    Comparable<String> c = o->{
      new Runnable() {
        @Override
        public void run() {
          System.out.println(o);
        }
      }.run();
      return 0;
    };
    Comparable<String> c1 = o->{
      o = "";
      new Runnable() {
        @Override
        public void run() {
          System.out.println(<error descr="Variable 'o' is accessed from within inner class, needs to be final or effectively final">o</error>);
        }
      }.run();
      return 0;
    };
  }
}

class IDEA114737 {
  private void on(String propertyName) {
    if (!"taskServices".equals(propertyName)) {
      return;
    }
    java.util.List<String> newList = null;
    Comparable<String> c1 = o -> {
      System.out.println(newList);
      return 0;
    };
  }
}

class IDEA128196 {
  void a() {
    int value;

    try {
      value = 1;
    } catch (Exception e) {
      return;
    }

    new Thread(() -> System.out.println(value));
  }
}

class FinalAssignmentInInitializer {
  private final String x;
  {
    Runnable r = () -> <error descr="Cannot assign a value to final variable 'x'">x</error> = "";
    x = "";
  }
}

class AssignmentToFinalInsideLambda {
  boolean isTrue() {
    return true;
  }

  Runnable r = () -> {
    final int i;
    if (isTrue()) {
      i = 1;
    } else {
      i = 0;
    }
  };

  void a() {
    Runnable r = () -> {
      final int i;
      if (isTrue()) {
        i = 1;
      } else {
        i = 0;
      }
    };
  }
}

class NonInitializedButWrittenTwice {
  private void test(boolean b) {
    int s;
    if(b) {
      s = 1;
      J is = () -> <error descr="Variable used in lambda expression should be final or effectively final">s</error>;
      System.out.println(is.m());
    }
    <error descr="Variable 's' might not have been initialized">s</error>++;
  }
}

class LocalInLoop {
  // IDEA-154224
  public static void main(String[] args) {
    long i;
    do {
      i = System.currentTimeMillis() + 1;
    } while (i < 3);
    Runnable r = () -> {
      System.out.println(<error descr="Variable used in lambda expression should be final or effectively final">i</error>); // <–ref an non final var
    };
    System.exit(0);
  }

  // IDEA-163280
  private static void test() {
    int a;
    for(int i = 0; i < 2; i++) {
      a = i;
      Runnable r = () -> System.out.println(<error descr="Variable used in lambda expression should be final or effectively final">a</error>);
      r.run();
    }
  }

  // IDEA-171790
  void test1() {
    java.util.function.Supplier<String> supplier = () -> "Some new string";
    String s;

    while ((s = supplier.get()) != null) {
      System.out.println(s);
    }

    Runnable r = () -> System.out.println(<error descr="Variable used in lambda expression should be final or effectively final">s</error>);
  }
}