import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Contract;

class InitializerTest {
  int x = Math.random() > 0.5 ? 0 : 1;
  int y = <warning descr="Condition 'x < 2' is always 'true'">x < 2</warning> ? 5 : 6;
  String z;

  {
    if(<warning descr="Condition 'y == 6' is always 'false'">y == 6</warning>) {
      System.out.println("oops");
    }
    z = "foo";
  }

  boolean b = <warning descr="Result of 'z.startsWith(\"bar\")' is always 'false'">z.startsWith("bar")</warning>;

  static final String ABC;
  static {
    if(Math.random() > 0.5) {
      ABC = null;
    } else {
      ABC = "foo";
    }
  }

  static final String XYZ = ABC.<warning descr="Method invocation 'toLowerCase' may produce 'NullPointerException'">toLowerCase</warning>();

  static {
    new InitializerTest(); // INITIALIZED is not initialized yet here
  }

  static final String INITIALIZED = "xyz".trim();

  {
    if(INITIALIZED == null) {
      System.out.println("Class is not initialized yet");
    }
  }

  static {
    if(<warning descr="Condition 'INITIALIZED == null' is always 'false'">INITIALIZED == null</warning>) {
      System.out.println("Class is not initialized yet");
    }
  }
}

class Constants {
  static final Object C1 = get();
  static final Object C2 = get();
  static final Object C3 = get();
  static final Object C4 = get();
  static final Object C5 = get();
  static final Object C6 = get();
  static final Object C7 = get();
  static final Object C8 = get();
  static final Object C9 = get();
  static final Object C10 = get();
  static final Object C11 = get();

  @Nullable
  static native Object get();
}

class <weak_warning descr="Class initializer is complex: data flow results could be imprecise">NotTooComplexInitializer</weak_warning> {
  static {
    int i = 1;
    for(Object obj = Constants.get(); obj != Constants.C5; obj = Constants.get()) {
      if (obj == Constants.C1) i = 2;
      if (obj == Constants.C2) i = 3;
      if (obj == Constants.C3) i = 4;
      if (obj == Constants.C4) i = 5;
      if (<warning descr="Condition 'obj == Constants.C5' is always 'false'">obj == Constants.C5</warning>) i = 6;
      if (obj == Constants.C6) i = 7;
      if (obj == Constants.C7) i = 8;
      if (obj == Constants.C8) i = 9;
      if (obj == Constants.C9) i = 10;
      if (obj == Constants.C10) i = 11;
      if (obj == Constants.C11) i = 12;
      if (i > 5 && obj == Constants.C3) {
        System.out.println("Never?");
      }
    }
  }
}
class <weak_warning descr="Class initializer is complex: data flow results could be imprecise">NotTooComplexMergingInitializer</weak_warning> {
  static {
    foo(Constants.C1 == null, Constants.C2 == null, Constants.C3 == null,
                  Constants.C4 == null, Constants.C5 == null, Constants.C6 == null,
                  Constants.C7 == null, Constants.C8 == null, Constants.C9 == null,
                  Constants.C10 == null, Constants.C11 == null);
  }

  native static void foo(boolean... args);
}