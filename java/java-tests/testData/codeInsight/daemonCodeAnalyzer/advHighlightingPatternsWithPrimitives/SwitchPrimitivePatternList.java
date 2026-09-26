package dfa;

public class SwitchPrimitivePatternList {
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
  }


  private static void testCharObject() {
    Character i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Character'">byte d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Character'">short d</error> -> System.out.println("1");//error
        case int d -> System.out.println("1");
        case long d -> System.out.println("1");
        case float d -> System.out.println("1");
        case double d -> System.out.println("1");
    }
  }

  private static void testByteObject() {
    Byte i = 0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Byte'">char d</error> -> System.out.println("1");//error
        case byte d -> System.out.println("1");
        case short d -> System.out.println("1");
        case int d -> System.out.println("1");
        case long d -> System.out.println("1");
        case float d -> System.out.println("1");
        case double d -> System.out.println("1");
    }
  }

  private static void testShortObject() {
    Short i = 0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Short'">char d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Short'">byte d</error> -> System.out.println("1");//error
        case short d -> System.out.println("1");
        case int d -> System.out.println("1");
        case long d -> System.out.println("1");
        case float d -> System.out.println("1");
        case double d -> System.out.println("1");
    }
  }

  private static void testIntObject() {
    Integer i = 0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">char d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Integer'">byte d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Integer'">short d</error> -> System.out.println("1");//error
        case int d -> System.out.println("1");
        case long d -> System.out.println("1");
        case float d -> System.out.println("1");
        case double d -> System.out.println("1");
    }
  }

  private static void testLongObject() {
    Long i = 0L;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Long'">char d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Long'">byte d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Long'">short d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">int d</error> -> System.out.println("1");//error
        case long d -> System.out.println("1");
        case float d -> System.out.println("1");
        case double d -> System.out.println("1");
    }
  }

  private static void testFloatObject() {
    Float i = 0F;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Float'">char d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Float'">byte d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Float'">short d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Float'">int d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Float'">long d</error> -> System.out.println("1");//error
        case float d -> System.out.println("1");
        case double d -> System.out.println("1");
    }
  }

  private static void testDoubleObject() {
    Double i = 0.0;
    switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Double'">char d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'byte', required: 'java.lang.Double'">byte d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'short', required: 'java.lang.Double'">short d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">int d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Double'">long d</error> -> System.out.println("1");//error
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">float d</error> -> System.out.println("1");//error
        case double d -> System.out.println("1");
    }
  }


  private static void testChar() {
    char i = 0;
    switch (i) {
      case <error descr="Duplicate unconditional pattern">char d</error> -> System.out.println("1");//error
        case byte d -> System.out.println("1");
        case short d -> System.out.println("1");
      case <error descr="Duplicate unconditional pattern">int d</error> -> System.out.println("1"); //error
      case <error descr="Duplicate unconditional pattern">long d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">float d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">double d</error> -> System.out.println("1");//error
    }
  }

  private static void testByte() {
    byte i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      case <error descr="Duplicate unconditional pattern">byte d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">short d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">int d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">long d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">float d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">double d</error> -> System.out.println("1");//error
    }
  }

  private static void testShort() {
    short i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      case byte d -> System.out.println("1");
      case <error descr="Duplicate unconditional pattern">short d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">int d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">long d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">float d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">double d</error> -> System.out.println("1");//error
    }
  }

  private static void testInt() {
    int i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      case byte d -> System.out.println("1");
      case short d -> System.out.println("1");
      case <error descr="Duplicate unconditional pattern">int d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">long d</error> -> System.out.println("1");//error
        case float d -> System.out.println("1");
      case <error descr="Duplicate unconditional pattern">double d</error> -> System.out.println("1");//error
    }
  }

  private static void testLong() {
    long i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      case byte d -> System.out.println("1");
      case short d -> System.out.println("1");
      case int d -> System.out.println("1");
      case long d -> System.out.println("1");
      case float d -> System.out.println("1");
      case double d -> System.out.println("1");
    }
  }

  private static void testFloat() {
    float i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      case byte d -> System.out.println("1");
      case short d -> System.out.println("1");
      case int d -> System.out.println("1");
      case long d -> System.out.println("1");
      case <error descr="Duplicate unconditional pattern">float d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">double d</error> -> System.out.println("1");//error
      case <error descr="Duplicate unconditional pattern">Float f</error> -> System.out.println();//error
    }
  }

  private static void testDouble() {
    double i = 0;
    switch (i) {
      case char d -> System.out.println("1");
      case byte d -> System.out.println("1");
      case short d -> System.out.println("1");
      case int d -> System.out.println("1");
      case long d -> System.out.println("1");
      case float d -> System.out.println("1");
      case double d -> System.out.println("1");
    }
  }
}
