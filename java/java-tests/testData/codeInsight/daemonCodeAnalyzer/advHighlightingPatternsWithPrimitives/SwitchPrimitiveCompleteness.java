package dfa;

public class SwitchPrimitiveCompleteness {
  public static void main(String[] args) {
    withErasureSwitch(1);
    testBoolean();
    testChar();
    testInt();
    testLong();
    testFloat();
    testDouble();

    testBooleanObject();
    testCharObject();
    testIntObject();
    testLongObject();
    testFloatObject();
    testDoubleObject();
    testNumber();
    testObject();
  }

  public static <T extends Integer> boolean withErasureSwitch(T i) {
    return switch (i) {
      case long a -> false;
    };
  }

  private static void testObject() {
    Object d = 0.0;

    switch (d) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Object'">1</error> -> System.out.println("1"); //error
        case double ob -> System.out.println("1");
    }
    switch (d) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Object'">2.0</error> -> System.out.println("1"); //error
    }

    switch (d) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Object'">2.0</error> -> System.out.println("1"); //error
        case Double ob -> System.out.println("1");
    }
    switch (d) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Object'">2.0</error> -> System.out.println("1"); //error
        case Double ob -> System.out.println("1");
        default -> System.out.println("2");
    }
    switch (d) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Object'">2.0</error> -> System.out.println("1"); //error
        case Object a -> System.out.println("2");
    }
  }

  private static void testNumber() {
    Number d = 0.0;

    switch (d) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Number'">1</error> -> System.out.println("1"); //error
        case double ob -> System.out.println("1");
    }
    switch (d) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Number'">2.0</error> -> System.out.println("1"); //error
    }

    switch (d) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Number'">2.0</error> -> System.out.println("1"); //error
        case Double ob -> System.out.println("1");
    }
    switch (d) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Number'">2.0</error> -> System.out.println("1"); //error
        case Double ob -> System.out.println("1");
        default -> System.out.println("2");
    }
    switch (d) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Number'">2.0</error> -> System.out.println("1"); //error
        case Object a -> System.out.println("2");
    }
  }

  private static void testDoubleObject() {
    Double d = 0.0;

    switch (d) {
      case 0.1 -> System.out.println("1");
      case double ob -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">d</error>) { //error
      case 2.0 -> System.out.println("1");
    }

    switch (d) {
      case 2.0 -> System.out.println("1");
      case Double ob -> System.out.println("1");
    }
    switch (d) {
      case 2.0 -> System.out.println("1");
      case <error descr="'switch' has both an unconditional pattern and a default label">Double ob</error> -> System.out.println("1"); //error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (d) {
      case 2.0 -> System.out.println("1");
      case Object a -> System.out.println("2");
    }
  }

  private static void testDouble() {
    double d = 0.0;

    switch (d) {
      case 0.1 -> System.out.println("1");
      case double ob -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">d</error>) { //error
      case 2.0 -> System.out.println("1");
    }

    switch (d) {
      case 2.0 -> System.out.println("1");
      case Double ob -> System.out.println("1");
    }
    switch (d) {
      case 2.0 -> System.out.println("1");
      case <error descr="'switch' has both an unconditional pattern and a default label">Double ob</error> -> System.out.println("1"); //error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (d) {
      case 2.0 -> System.out.println("1");
      case Object a -> System.out.println("2");
    }
  }

  private static void testFloat() {
    float f = 0.0F;

    switch (f) {
      case 2.0F -> System.out.println("1");
      case float ob -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">f</error>) { //error
      case 2.0F -> System.out.println("1");
    }

    switch (f) {
      case 2.0F -> System.out.println("1");
      case Float ob -> System.out.println("1");
    }
    switch (f) {
      case 2.0F -> System.out.println("1");
      case <error descr="'switch' has both an unconditional pattern and a default label">Float ob</error> -> System.out.println("1"); //error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (f) {
      case 2.0F -> System.out.println("1");
      case Object a -> System.out.println("2");
    }
  }

  private static void testFloatObject() {
    Float f = 0.0F;

    switch (f) {
      case 2.0F -> System.out.println("1");
      case float ob -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">f</error>) {
      case 2.0F -> System.out.println("1");
    }

    switch (f) {
      case 2.0F -> System.out.println("1");
      case Float ob -> System.out.println("1");
    }
    switch (f) {
      case 2.0F -> System.out.println("1");
      case <error descr="'switch' has both an unconditional pattern and a default label">Float ob</error> -> System.out.println("1"); //error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (f) {
      case 2.0F -> System.out.println("1");
      case Object a -> System.out.println("2");
    }

  }

  private static void testLongObject() {
    Long l = 1L;

    switch (l) {
      case 2L -> System.out.println("1");
      case long ob -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">l</error>) {
      case 1L -> System.out.println("1");
    }

    switch (l) {
      case 3L -> System.out.println("1");
      case Long ob -> System.out.println("1");
    }
    switch (l) {
      case 3L -> System.out.println("1");
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Long'">Double ob</error> -> System.out.println("1"); //error
        default -> System.out.println("2");
    }
    switch (l) {
      case 1L -> System.out.println("1");
      case Object a -> System.out.println("2");
    }
  }

  private static void testLong() {
    long l = 1L;

    switch (l) {
      case 2L -> System.out.println("1");
      case long ob -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">l</error>) { //error
      case 1L -> System.out.println("1");
    }

    switch (l) {
      case 3L -> System.out.println("1");
      case Long ob -> System.out.println("1");
    }
    switch (l) {
      case 3L -> System.out.println("1");
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'long'">Double ob</error> -> System.out.println("1"); //error
        default -> System.out.println("2");
    }
    switch (l) {
      case 1L -> System.out.println("1");
      case long a -> System.out.println("2");
    }
    switch (l) {
      case 1L -> System.out.println("1");
      case Object a -> System.out.println("2");
    }

  }

  private static void testIntObject() {
    Integer i = 1;
    switch (i) {
      case 2 -> System.out.println("1");
      case int ob -> System.out.println("1");
    }
    switch (i) {
      case 1 -> System.out.println("1");
    }

    switch (i) {
      case 3 -> System.out.println("1");
      case <error descr="'switch' has both an unconditional pattern and a default label">Integer ob</error> -> System.out.println("1"); //error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (i) {
      case 1 -> System.out.println("1");
      case Integer a -> System.out.println("2");
    }
    switch (i) {
      case 1 -> System.out.println("1");
      case Object a -> System.out.println("2");
    }
  }

  private static void testInt() {
    int i = 1;
    switch (i) {
      case 2 -> System.out.println("1");
      case int ob -> System.out.println("1");
    }
    switch (i) {
      case 1 -> System.out.println("1");
    }

    switch (i) {
      case 3 -> System.out.println("1");
      case <error descr="'switch' has both an unconditional pattern and a default label">Integer ob</error> -> System.out.println("1"); //error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (i) {
      case 1 -> System.out.println("1");
      case Integer a -> System.out.println("2");
    }
    switch (i) {
      case 1 -> System.out.println("1");
      case Object a -> System.out.println("2");
    }
  }

  private static void testCharObject() {
    Character b = 'a';
    switch (b) {
      case 'a' -> System.out.println("1");
      case char ob -> System.out.println("1");
    }
    switch (b) {
      case 'a' -> System.out.println("1");
    }

    switch (b) {
      case 'a' -> System.out.println("1");
      case <error descr="'switch' has both an unconditional pattern and a default label">Character ob</error> -> System.out.println("1"); //error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (b) {
      case 'a' -> System.out.println("1");
      case Character a -> System.out.println("2");
    }
    switch (b) {
      case 'a' -> System.out.println("1");
      case Object a -> System.out.println("2");
    }
  }

  private static void testChar() {
    char b = 'a';
    switch (b) {
      case 'a' -> System.out.println("1");
      case char ob -> System.out.println("1");
    }
    switch (b) {
      case 'a' -> System.out.println("1");
    }

    switch (b) {
      case 'a' -> System.out.println("1");
      case <error descr="'switch' has both an unconditional pattern and a default label">Character ob</error> -> System.out.println("1"); //error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (b) {
      case 'a' -> System.out.println("1");
      case Character a -> System.out.println("2");
    }
    switch (b) {
      case 'a' -> System.out.println("1");
      case Object a -> System.out.println("2");
    }
  }

  private static void testBooleanObject() {
    Boolean b = true;
    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">b</error>) { //error
      case false -> System.out.println("1");
    }
    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
      <error descr="'switch' has all boolean values and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
      case <error descr="'switch' has all boolean values and an unconditional pattern">Boolean a</error> -> System.out.println("2"); //error
    }
    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
      case boolean a -> System.out.println("2");
    }

    switch (b) {
      case false -> System.out.println("1");
      case boolean a -> System.out.println("2");
    }

    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
      case <error descr="'switch' has all boolean values and an unconditional pattern">Object a</error> -> System.out.println("2"); //error
    }
  }

  private static void testBoolean() {
    boolean b = true;
    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">b</error>) { //error
      case false -> System.out.println("1");
    }
    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
      <error descr="'switch' has all boolean values and a default label">default</error> -> System.out.println("2"); //error
    }
    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
      case <error descr="'switch' has all boolean values and an unconditional pattern">Boolean a</error> -> System.out.println("2"); //error
    }
    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
      case <error descr="'switch' has all boolean values and an unconditional pattern">boolean a</error> -> System.out.println("2"); //error
    }

    switch (b) {
      case false -> System.out.println("1");
      case boolean a -> System.out.println("2");
    }

    switch (b) {
      case true -> System.out.println("1");
      case false -> System.out.println("1");
      case <error descr="'switch' has all boolean values and an unconditional pattern">Object a</error> -> System.out.println("2"); //error
    }
  }
}
