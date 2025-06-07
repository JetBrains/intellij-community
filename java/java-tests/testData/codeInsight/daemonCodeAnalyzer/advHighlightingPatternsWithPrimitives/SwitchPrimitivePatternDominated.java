package dfa;

public class SwitchPrimitivePatternDominated {
  public static void main(String[] args) {
    testBoolean();
    testChar();
    testByte();
    testShort();
    testInt();
    testLong();
    testFloat();
    testDouble();
  }

  private static void testDouble() {
    Object o = 1;
    switch (o) {
      case byte a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">int a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">float a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">double a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">double a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case double a -> System.out.println("1");
      case Byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Byte a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case Short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Short a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case Character a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Character a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Integer a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case Integer a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Long a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case Long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Float a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case Float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Double a'">double a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case Double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Number a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Number a'">double a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case Number a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Object a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Object a'">double a</error> -> System.out.println("1");//error
    }
    switch (o) {
      case double a -> System.out.println("1");
      case Object a -> System.out.println("1");
    }
  }


  private static void testFloat() {
    Object o = 1;
    switch (o) {
      case byte a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'float a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'float a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'float a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'float a'">float a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'float a'">float a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">float a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case float a -> System.out.println("1");
      case Byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Byte a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case Short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Short a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case Character a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Character a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Integer a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case Integer a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Long a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case Long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Float a'">float a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case Float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Double a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case Double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Number a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Number a'">float a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case Number a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Object a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Object a'">float a</error> -> System.out.println("1");//error
    }
    switch (o) {
      case float a -> System.out.println("1");
      case Object a -> System.out.println("1");
    }
  }

  private static void testLong() {
    Object o = 1;
    switch (o) {
      case byte a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">int a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">long a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">long a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case long a -> System.out.println("1");
      case Byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Byte a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case Short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Short a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case Character a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Character a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Integer a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case Integer a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Long a'">long a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case Long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Float a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case Float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Double a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case Double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Number a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Number a'">long a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case Number a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Object a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Object a'">long a</error> -> System.out.println("1");//error
    }
    switch (o) {
      case long a -> System.out.println("1");
      case Object a -> System.out.println("1");
    }
  }

  private static void testInt() {
    Object o = 1;
    switch (o) {
      case byte a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'int a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'int a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'int a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'int a'">int a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'int a'">int a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">int a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">int a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case int a -> System.out.println("1");
      case Byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Byte a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case Short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Short a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case Character a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Character a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Integer a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Integer a'">int a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case Integer a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Long a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case Long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Float a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case Float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Double a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case Double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Number a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Number a'">int a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case Number a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Object a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Object a'">int a</error> -> System.out.println("1");//error
    }
    switch (o) {
      case int a -> System.out.println("1");
      case Object a -> System.out.println("1");
    }

  }

  private static void testShort() {
    Object o = 1;
    switch (o) {
      case byte a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'short a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'short a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'short a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'int a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'float a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case short a -> System.out.println("1");
      case Byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Byte a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case Short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Short a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Short a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case Character a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Character a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Integer a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case Integer a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Long a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case Long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Float a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case Float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Double a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case Double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Number a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Number a'">short a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case Number a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Object a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Object a'">short a</error> -> System.out.println("1");//error
    }
    switch (o) {
      case short a -> System.out.println("1");
      case Object a -> System.out.println("1");
    }

  }

  private static void testByte() {
    Object o = 1;
    switch (o) {
      case byte a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'byte a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'byte a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'short a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'int a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'float a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case byte a -> System.out.println("1");
      case Byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Byte a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Byte a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case Short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Short a -> System.out.println("1");
      case byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case Character a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Character a -> System.out.println("1");
      case byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Integer a -> System.out.println("1");
      case byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case Integer a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Long a -> System.out.println("1");
      case byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case Long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Float a -> System.out.println("1");
      case byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case Float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Double a -> System.out.println("1");
      case byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case Double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Number a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Number a'">byte a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case Number a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Object a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Object a'">byte a</error> -> System.out.println("1");//error
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case Object a -> System.out.println("1");
    }
  }

  private static void testChar() {
    Object o = 1;
    switch (o) {
      case char a -> System.out.println("1");
      case byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case byte a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case short a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'char a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'char a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case int a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'int a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case int a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case long a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'long a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case float a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'float a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case double a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'double a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case char a -> System.out.println("1");
      case Byte a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Byte a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case Short a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Short a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case Character a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Character a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Character a'">char a</error> -> System.out.println("1");//error
        default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Integer a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case Integer a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Long a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case Long a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Float a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case Float a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case Double a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case Double a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Number a -> System.out.println("1");
      case char a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }
    switch (o) {
      case char a -> System.out.println("1");
      case Number a -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + o);
    }

    switch (o) {
      case Object a -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'Object a'">char a</error> -> System.out.println("1");//error
    }
    switch (o) {
      case char a -> System.out.println("1");
      case Object a -> System.out.println("1");
    }
  }

  private static void testBoolean() {
    Object o = 1;
    switch (o) {
      case boolean b -> System.out.println("2");
      case long b -> System.out.println("2");
      default -> System.out.println("1");
    }
    switch (o) {
      case long b -> System.out.println("2");
      case boolean b -> System.out.println("2");
      default -> System.out.println("1");
    }
    switch (o) {
      case boolean b -> System.out.println("2");
      case Boolean b -> System.out.println("2");
      default -> System.out.println("1");
    }
    switch (o) {
      case boolean b -> System.out.println("2");
      case Object b -> System.out.println("2");
    }
    switch (o) {
      case Boolean b -> System.out.println("2");
      case <error descr="Label is dominated by a preceding case label 'Boolean b'">boolean b</error> -> System.out.println("2");//error
        default -> System.out.println("1");
    }

    switch (o) {
      case Object b -> System.out.println("2");
      case <error descr="Label is dominated by a preceding case label 'Object b'">boolean b</error> -> System.out.println("2");//error
    }
  }

  static void switchInteger(Integer i) {
    switch (i) {
      case Integer i2 -> System.out.println("int" + i2);
      case long l1 -> System.out.println("long" + l1);
    }
  }

  static void switchDouble(double i) {
    switch (i) {
      case double i2 -> System.out.println("double" + i2);
      case long l1 -> System.out.println("long" + l1);
    }
  }
}
