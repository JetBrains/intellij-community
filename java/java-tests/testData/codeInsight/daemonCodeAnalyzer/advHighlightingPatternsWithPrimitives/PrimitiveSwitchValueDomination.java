public class PrimitiveSwitchValueDomination {


  static void main() {
  }

  public void testNullDominated() {
    Integer integer = 1;
    switch (integer) {
      case Integer i -> {
      }
      case null -> {
      }
    }
  }

  interface A1{}
  interface A2{}

  public static void testInterface() {
    A1 a1 = new A1() {
    };
    switch (a1) {
      case A1 a-> System.out.println(1);
      case A2 a-> System.out.println(1);
    }
  }
  public static void testWrapper() {
    int a = 1;
    switch (a) {
      case Number number -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'Number number'">1</error> -> System.out.println(1);  //error
    }
  }

  public static void testWrapperInt() {
    int a = 1;
    switch (a) {
      case Integer number -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'Integer number'">1</error> -> System.out.println(1); //error
    }
  }

  public static void testWrapperChar() {
    int a = 1;
    switch (a) {
      case char number -> System.out.println(2);
      case 1 -> System.out.println(1);
      default -> System.out.println("");
    }
  }

  public static void testWrapperObject() {
    Object a = 1;

    switch (a) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Object'">1</error> -> System.out.println(1); //error
      case char number -> System.out.println(2);
      default -> System.out.println("");
    }
  }

  public static void testString() {
    String a = "abc";
    switch (a) {
      case CharSequence string -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'CharSequence string'">"abc"</error> -> System.out.println(2); //error
    }
  }

  public static void testEnum() {
    enum ABC {A, B, ABC,}
    Object a = ABC.A;
    switch (a) {
      case ABC aa -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'ABC aa'">ABC.A</error> -> System.out.println(2); //error
    }
  }

  public static void testByte() {
    int a = 1;
    switch (a) {
      case char c -> System.out.println(2);
      case (byte) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Byte.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Byte.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'byte c'">(byte) 0</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'byte c'">Byte.MAX_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'byte c'">Byte.MIN_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case short c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'short c'">(byte) 0</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'short c'">Byte.MAX_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'short c'">Byte.MIN_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">(byte) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">Byte.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">Byte.MIN_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Byte.MIN_VALUE</error> -> System.out.println(2); //error
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">(byte) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Byte.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">(byte) 0</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Byte.MAX_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Byte.MIN_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">(byte) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Byte.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {

      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Byte.MIN_VALUE</error> -> System.out.println(2); //error
    }
  }

  public static void testChar() {
    int a = 1;
    switch (a) {
      case char c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'char c'">(char) 0</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'char c'">Character.MAX_VALUE</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'char c'">Character.MIN_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (char) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Character.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Character.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (char)(128) -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (char)(127) -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case short c -> System.out.println(2);
      case (char) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Character.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Character.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">(char) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">Character.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">Character.MIN_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Character.MIN_VALUE</error> -> System.out.println(2);  //error
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">(char) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Character.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">(char) 0</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Character.MAX_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Character.MIN_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">(char) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Character.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Character.MIN_VALUE</error> -> System.out.println(2); //error
    }

    int a2 = 1;
    switch (a2) {
      case Integer c -> System.out.println(2);
      case Character.MIN_VALUE -> System.out.println(2);
    }
  }

  public static void testShort() {
    int a = 1;
    switch (a) {
      case char c -> System.out.println(2);
      case (short) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Short.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Short.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (short) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Short.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Short.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (short)(128) -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (short)(127) -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case short c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'short c'">(short) 0</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'short c'">Short.MAX_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'short c'">Short.MIN_VALUE</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">(short) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">Short.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">Short.MIN_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Short.MIN_VALUE</error> -> System.out.println(2);  //error
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">(short) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Short.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">(short) 0</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Short.MAX_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Short.MIN_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">(short) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Short.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Short.MIN_VALUE</error> -> System.out.println(2); //error
    }

    int a2 = 1;
    switch (a2) {
      case Integer c -> System.out.println(2);
      case Short.MIN_VALUE -> System.out.println(2);
    }
  }

  public static void testInt() {
    int a = 1;
    switch (a) {
      case char c -> System.out.println(2);
      case (int) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Integer.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Integer.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (int) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Integer.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Integer.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (int)(128) -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (int)(127) -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case short c -> System.out.println(2);
      case (int) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Integer.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Integer.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">(int) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">Integer.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case int c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'int c'">Integer.MIN_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Integer.MIN_VALUE</error> -> System.out.println(2);  //error
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">(int) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Integer.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Integer.MIN_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Integer.MIN_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case float c -> System.out.println(2);
      case (int) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case Integer.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case Integer.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">(int) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Integer.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Integer.MIN_VALUE</error> -> System.out.println(2); //error
    }

    int a2 = 1;
    switch (a2) {
      case Integer c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'Integer c'">Integer.MIN_VALUE</error> -> System.out.println(2);//error
    }
  }

  public static void testLong() {
    long a = 1;
    switch (a) {
      case char c -> System.out.println(2);
      case (long) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Long.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Long.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Long.MAX_VALUE - (long) 9.22e18 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case 0x001fffffffffffffL -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (long) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Long.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Long.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Long.MAX_VALUE - (long) 9.22e18 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case 0x001fffffffffffffL -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (long)(128) -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (long)(127) -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case short c -> System.out.println(2);
      case (long) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Long.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Long.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Long.MAX_VALUE - (long) 9.22e18 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case 0x001fffffffffffffL -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case int c -> System.out.println(2);
      case (long) 0 -> System.out.println(2);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case Long.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case Long.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case 0x001fffffffffffffL -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Long.MIN_VALUE</error> -> System.out.println(2);  //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">(long) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Long.MAX_VALUE</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">Long.MAX_VALUE - (long) 9.22e18</error> -> System.out.println(2); //error
    }

    switch (a) {
      case long c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'long c'">0x001fffffffffffffL</error> -> System.out.println(2); //error
    }

    switch (a) {
      case float c -> System.out.println(2);
      case (long) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case Long.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case Long.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case Long.MAX_VALUE - (long) 9.22e18 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case 0x001fffffffffffffL -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case double c -> System.out.println(2);
      case (long) 0 -> System.out.println(2);
    }


    switch (a) {
      case double c -> System.out.println(2);
      case Long.MAX_VALUE -> System.out.println(2);
      default ->  System.out.println(1);
    }

    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case double c -> System.out.println(2);
      case Long.MIN_VALUE -> System.out.println(2);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case double c -> System.out.println(2);
      case Long.MAX_VALUE - (long) 9.22e18 -> System.out.println(2);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case double c -> System.out.println(2);
      case 0x001fffffffffffffL -> System.out.println(2);
    }
  }

  public static void testFloat() {
    float a = 1;
    switch (a) {
      case char c -> System.out.println(2);
      case (float) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case Float.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case Float.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case Float.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case +0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case -0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Float.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Float.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case 0x1p63f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case 0x1p5f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case 0x1p62f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case 0x1p31f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (float) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Float.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case Float.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case Float.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case +0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case -0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Float.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Float.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case 0x1p63f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case 0x1p5f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case 0x1p62f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case 0x1p31f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case short c -> System.out.println(2);
      case (float) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Float.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case Float.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case Float.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case +0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case -0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Float.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Float.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case 0x1p63f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case 0x1p5f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case 0x1p62f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case 0x1p31f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case int c -> System.out.println(2);
      case (float) 0 -> System.out.println(2);
    }


    switch (a) {
      case int c -> System.out.println(2);
      case Float.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case Float.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case Float.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case +0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case -0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case Float.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case Float.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case 0x1p63f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case 0x1p5f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case 0x1p62f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case 0x1p31f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case 0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case Float.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case Float.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case Float.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case +0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case -0.0f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case long c -> System.out.println(2);
      case Float.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case 0x1p63f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case 0x1p5f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case 0x1p62f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case 0x1p31f -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case long c -> System.out.println(2);
      case (float) 0 -> System.out.println(2);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case Float.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">(float) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Float.NaN</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Float.NEGATIVE_INFINITY</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Float.POSITIVE_INFINITY</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">+0.0f</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">-0.0f</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Float.MAX_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">Float.MIN_VALUE</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">0x1p63f</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">0x1p5f</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">0x1p62f</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'float c'">0x1p31f</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">(float) 0</error> -> System.out.println(2); //error
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Float.NaN</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Float.NEGATIVE_INFINITY</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Float.POSITIVE_INFINITY</error> -> System.out.println(2);  //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">+0.0f</error> -> System.out.println(2); //error
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">-0.0f</error> -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Float.MAX_VALUE</error> -> System.out.println(2);
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">Float.MIN_VALUE</error> -> System.out.println(2);
    }

    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">0x1p63f</error> -> System.out.println(2);
    }
    
    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">0x1p5f</error> -> System.out.println(2);
    }
    
    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">0x1p62f</error> -> System.out.println(2);
    }
    
    switch (a) {
      case double c -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'double c'">0x1p31f</error> -> System.out.println(2);
    }
  }

  public static void testDouble() {
    double a = 1;
    switch (a) {
      case char c -> System.out.println(2);
      case (double) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case Double.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case Double.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case Double.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case +0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case -0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Double.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case Double.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case 0x1p63 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case char c -> System.out.println(2);
      case 0x1p5 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case 0x1p62 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case char c -> System.out.println(2);
      case 0x1p31 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case (double) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Double.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case Double.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case Double.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case +0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case -0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Double.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case Double.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case 0x1p63 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case byte c -> System.out.println(2);
      case 0x1p5 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case 0x1p62 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case byte c -> System.out.println(2);
      case 0x1p31 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }


    switch (a) {
      case short c -> System.out.println(2);
      case (double) 0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Double.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case Double.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case Double.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case +0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case -0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Double.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case Double.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case short c -> System.out.println(2);
      case 0x1p63 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case 0x1p5 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case 0x1p62 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case short c -> System.out.println(2);
      case 0x1p31 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case int c -> System.out.println(2);
      case (double) 0 -> System.out.println(2);
    }


    switch (a) {
      case int c -> System.out.println(2);
      case Double.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case Double.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case Double.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case +0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case -0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case Double.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case Double.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case int c -> System.out.println(2);
      case 0x1p63 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case 0x1p5 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case 0x1p62 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case int c -> System.out.println(2);
      case 0x1p31 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case 0d -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case Double.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case Double.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case Double.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case +0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case -0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case Double.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case 0x1p63 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case 0x1p5 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case 0x1p62 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case long c -> System.out.println(2);
      case 0x1p31 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case long c -> System.out.println(2);
      case (double) 0 -> System.out.println(2);
    }

    switch (a) {
      case long c -> System.out.println(2);
      case Double.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (<error descr="'switch' statement does not cover all possible input values">a</error>) {
      case float c -> System.out.println(2);
      case (double) 0 -> System.out.println(2);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case Double.NaN -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case Double.NEGATIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case Double.POSITIVE_INFINITY -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case +0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case -0.0 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case Double.MAX_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }

    switch (a) {
      case float c -> System.out.println(2);
      case Double.MIN_VALUE -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case 0x1p63 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case 0x1p5 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case 0x1p62 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
    
    switch (a) {
      case float c -> System.out.println(2);
      case 0x1p31 -> System.out.println(2);
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
  }
}
