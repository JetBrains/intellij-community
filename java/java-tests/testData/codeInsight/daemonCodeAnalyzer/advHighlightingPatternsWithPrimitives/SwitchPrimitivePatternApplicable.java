package dfa;

public class SwitchPrimitivePatternApplicable {
  public static void main(String[] args) {
    testChar();
    testByte();
    testShort();
    testInt();
    testLong();
    testFloat();
    testDouble();

    testCharObject();
    testByteObject();
    testShortObject();
    testIntObject();
    testLongObject();
    testFloatObject();
    testDoubleObject();

    testNumberObject();
    testObject();
  }

  private static void testObject() {
    Number i = 0.0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Number'">char d</error> -> System.out.println("1"); //error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case int d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case int d -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case double d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case double d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Number'">Character d</error> -> System.out.println("1"); //error
    }
    switch (i) {
      case Byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Short d -> System.out.println("1");
    }
    switch (i) {
      case Short d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Integer d -> System.out.println("1");
    }
    switch (i) {
      case Integer d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Long d -> System.out.println("1");
    }
    switch (i) {
      case Long d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Float d -> System.out.println("1");
    }
    switch (i) {
      case Float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Double d -> System.out.println("1");
    }
    switch (i) {
      case Double d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testNumberObject() {
    Number i = 0.0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Number'">char d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case int d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case int d -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case double d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case double d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Number'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Short d -> System.out.println("1");
    }
    switch (i) {
      case Short d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Integer d -> System.out.println("1");
    }
    switch (i) {
      case Integer d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Long d -> System.out.println("1");
    }
    switch (i) {
      case Long d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Float d -> System.out.println("1");
    }
    switch (i) {
      case Float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Double d -> System.out.println("1");
    }
    switch (i) {
      case Double d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testDoubleObject() {
    Double i = 0.0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Double'">char d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Double'">byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Double'">short d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">int d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">int d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Double'">long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Double'">long d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">float d</error> -> System.out.println("1");
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case double d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">float d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case double d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Double'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Double'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Double'">Short d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Double'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Double'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Double'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Double'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Double d -> System.out.println("1");
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testFloatObject() {
    Float i = 0F;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Float'">char d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Float'">byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Float'">short d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Float'">int d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Float'">int d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Float'">long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Float'">long d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case double d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Float'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Float'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Float'">Short d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Float'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Float'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Float'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Float d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Float'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testLongObject() {
    Long i = 0L;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Long'">char d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Long'">byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Long'">short d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">int d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">int d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case float d -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case double d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case double d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Long'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Long'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Long'">Short d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Long'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Long'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Long d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Long'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Long'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testIntObject() {
    Integer i = 0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">char d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Integer'">byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Integer'">short d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Integer'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Integer'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Integer'">Short d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Integer'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case Integer d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Integer'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Integer'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Integer'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }

  }

  private static void testShortObject() {
    Short i = 0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Short'">char d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Short'">byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
    }
    switch (i) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Short'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Short'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case Short d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Short d</error> -> System.out.println("1");//error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Short'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Short'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Short'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Short'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }


  private static void testCharObject() {
    Character i = 0;
    switch (i) {
      case char d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Character'">byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Character'">short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }

    switch (i) {
      case Character d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Character'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Character'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Character'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Character'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Character'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Character'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Character'">Number d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testByteObject() {
    Byte i = 0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Byte'">char d</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
    }
    switch (i) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Byte'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Byte d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Byte'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Byte'">Integer d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Byte'">Long d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Byte'">Float d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Byte'">Double d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }


  private static void testDouble() {
    double i = 0;

    switch (i) {
      case int i2 -> System.out.println("1");
      case <error descr="'null' cannot be converted to 'double'">null</error>, default -> System.out.println("1");
    }
    switch (i) {
      case int i2 -> System.out.println("1");
      case <error descr="'null' cannot be converted to 'double'">null</error> -> System.out.println("1");
      default -> System.out.println("1");
    }

    switch (i) {
      case char d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case int d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case int d -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case double d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case <error descr="'switch' has both an unconditional pattern and a default label">double d</error> -> System.out.println("1");//error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> throw new IllegalStateException("Unexpected value: " + i);//error
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'double'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'double'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'double'">Short d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'double'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'double'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'double'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'double'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Double d -> System.out.println("1");
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testFloat() {
    float i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case int d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case int d -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="'switch' has both an unconditional pattern and a default label">float d</error> -> System.out.println("1");//error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> throw new IllegalStateException("Unexpected value: " + i);//error
    }
    switch (i) {
      case <error descr="'switch' has both an unconditional pattern and a default label">double d</error> -> System.out.println("1");//error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> throw new IllegalStateException("Unexpected value: " + i);//error
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'float'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'float'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'float'">Short d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'float'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'float'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'float'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Float d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'float'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testLong() {
    long i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case int d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case float d -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case double d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case double d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'long'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'long'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'long'">Short d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'long'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'long'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Long d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'long'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'long'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testInt() {
    int i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'int'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'int'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'int'">Short d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'int'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case Integer d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'int'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'int'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'int'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }

  }

  private static void testShort() {
    short i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
    }
    switch (i) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'short'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'short'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case Short d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Short d</error> -> System.out.println("1");//error
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'short'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'short'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'short'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'short'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }


  private static void testChar() {
    char i = 0;
    switch (i) {
      case char d -> System.out.println("1");
    }
    switch (i) {
      case byte d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
      default -> System.out.println("1");
    }
    switch (i) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }

    switch (i) {
      case Character d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Byte', required: 'char'">Byte d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'char'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'char'">Integer d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'char'">Long d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'char'">Float d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'char'">Double d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Number', required: 'char'">Number d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }

  private static void testByte() {
    byte i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
    switch (i) {
      case byte d -> System.out.println("1");
    }
    switch (i) {
      case short d -> System.out.println("1");
    }
    switch (i) {
      case int d -> System.out.println("1");
    }
    switch (i) {
      case long d -> System.out.println("1");
    }
    switch (i) {
      case float d -> System.out.println("1");
    }
    switch (i) {
      case double d -> System.out.println("1");
    }

    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Character', required: 'byte'">Character d</error> -> System.out.println("1");//error
    }
    switch (i) {
      case Byte d -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Short', required: 'byte'">Short d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'byte'">Integer d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Long', required: 'byte'">Long d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Float', required: 'byte'">Float d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case <error descr="Incompatible types. Found: 'java.lang.Double', required: 'byte'">Double d</error> -> System.out.println("1");//error
        default -> System.out.println("1");
    }
    switch (i) {
      case Number d -> System.out.println("1");
    }
    switch (i) {
      case Object d -> System.out.println("1");
    }
  }
}
