import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  boolean b = <warning descr="Condition 'z.startsWith(\"bar\")' is always 'false'">z.startsWith("bar")</warning>;

  static final String ABC;
  static {
    if(Math.random() > 0.5) {
      ABC = null;
    } else {
      ABC = "foo";
    }
  }

  static final String XYZ = ABC.<warning descr="Method invocation 'toLowerCase' may produce 'java.lang.NullPointerException'">toLowerCase</warning>();

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

  static Object get() {
    System.out.println();
    return new Object();
  }
}

class <weak_warning descr="Class initializer is too complex to analyze by data flow algorithm">TooComplexInitializer</weak_warning> {
  // This test just checks that "too complex" warning is placed correctly on class name,
  // not that this particular code always must be considered as "too complex".
  // If in future this code will become not too complex, that's fine, just update test to make it even more complex
  static {
    int i = 1;
    for(Object obj = Constants.get(); obj != Constants.C5; obj = Constants.get()) {
      if (obj == Constants.C1) i = 2;
      if (obj == Constants.C2) i = 3;
      if (obj == Constants.C3) i = 4;
      if (obj == Constants.C4) i = 5;
      if (obj == Constants.C5) i = 6;
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