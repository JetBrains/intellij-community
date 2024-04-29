package dfa;

public class SwitchRecordPrimitive {

  record RecordBool(boolean source) {
  }

  record RecordInt(int source) {
  }

  record RecordLong(long source) {
  }

  record RecordDouble(double source) {
  }

  record RecordByte(byte source) {
  }

  record RecordChar(char source) {
  }

  record RecordBoolObj(Boolean source) {
  }

  record RecordIntObj(Integer source) {
  }

  record RecordLongObj(Long source) {
  }

  record RecordDoubleObj(Double source) {
  }

  record RecordCharObj(Character source) {
  }

  record RecordNumber(Number source) {
  }

  record RecordRecordInt(RecordInt source) {
  }

  public static void main(String[] args) {
    testForRecordBool(new RecordBool(true));
    testForRecordBoolObj(new RecordBoolObj(true));

    testForRecordChar(new RecordChar('a'));
    testForRecordCharObj(new RecordCharObj('a'));

    testForRecordByte(new RecordByte((byte) 0));

    testForRecordInt(new RecordInt(1));
    testForRecordIntObj(new RecordIntObj(1));

    testForRecordLong(new RecordLong(1));
    testForRecordLongObj(new RecordLongObj(1L));

    testForRecordDouble(new RecordDouble(1));
    testForRecordDoubleObj(new RecordDoubleObj(1.0));


    testForRecordNumber(new RecordNumber(1));
    testForRecordRecordInt(new RecordRecordInt(new RecordInt(1)));

    exhaustiveByPrimitives();
  }


  public static int exhaustiveByPrimitives() {
    RecordIntObj r = new RecordIntObj(1);
    switch (r) {
      case RecordIntObj(int x) -> System.out.println("1");
    }
    switch (r) {
      case RecordIntObj(double x) -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">r</error>) { //error
      case RecordIntObj(float x) -> System.out.println("1");
    }
    return 1;
  }

  private static void testForRecordRecordInt(RecordRecordInt source) {
    switch (source) {
      case RecordRecordInt(RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'boolean'">boolean source1</error>)) -> System.out.println("1"); //error
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(char source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Character'">Character source1</error>)) -> System.out.println("1");//error
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordRecordInt(RecordInt(int source1))'">RecordRecordInt(RecordInt(char source1))</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(Integer source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(Integer source1)) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordRecordInt(RecordInt(Integer source1))'">RecordRecordInt(RecordInt(int source1))</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(Object source1)) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordRecordInt(RecordInt(Object source1))'">RecordRecordInt(RecordInt(int source1))</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(Object source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(Number source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(Object source1)) -> System.out.println("1");
    }
  }

  private static void testForRecordNumber(RecordNumber source) {
    switch (source) {
      case RecordNumber(<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordNumber(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'char'">char source1</error>) -> System.out.println("1");//error
      case RecordNumber(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Number'">Character source1</error>) -> System.out.println("1");//error
      case RecordNumber(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(int source1) -> System.out.println("1");
      case RecordNumber(<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'char'">char source1</error>) -> System.out.println("1");//error
    }
    switch (<error descr="'switch' statement does not cover all possible input values">source</error>) {//error
      case RecordNumber(int source1) -> System.out.println("1");
      case RecordNumber(Integer source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(Integer source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordNumber(Integer source1)'">RecordNumber(int source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordNumber(int source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordNumber(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordNumber(Object source1)'">RecordNumber(int source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordNumber(int source1) -> System.out.println("1");
      case RecordNumber(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(int source1) -> System.out.println("1");
      case RecordNumber(Number source1) -> System.out.println("1");
      case RecordNumber(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(int source1) -> System.out.println("1");
      case RecordNumber(Number source1) -> System.out.println("1");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">source</error>) {//error
      case RecordNumber(int source1) -> System.out.println("1");
    }
  }

  private static void testForRecordDoubleObj(RecordDoubleObj source) {
    switch (source) {
      case RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'char'">char source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(double source1) -> System.out.println("1");
      case RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'int'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'int'">int source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(double source1) -> System.out.println("1");
      case RecordDoubleObj(Double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(Double source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordDoubleObj(Double source1)'">RecordDoubleObj(double source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'float'">float source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(Double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Double'">Integer source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(double source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordDoubleObj(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordDoubleObj(Object source1)'">RecordDoubleObj(double source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(double source1) -> System.out.println("1");
      case RecordDoubleObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'int'">int source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(Number source1) -> System.out.println("1");
      case RecordDoubleObj(Object source1) -> System.out.println("1");
    }
  }

  private static void testForRecordDouble(RecordDouble source) {
    switch (source) {
      case RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(char source1) -> System.out.println("1");
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(double source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordDouble(double source1)'">RecordDouble(int source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDouble(int source1) -> System.out.println("1");
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(double source1) -> System.out.println("1");
      case RecordDouble(Double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(Double source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordDouble(Double source1)'">RecordDouble(double source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDouble(float source1) -> System.out.println("1");
      case RecordDouble(Double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Integer'">Integer source1</error>) -> System.out.println("1");//error
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(double source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordDouble(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordDouble(Object source1)'">RecordDouble(double source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDouble(double source1) -> System.out.println("1");
      case RecordDouble(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(int source1) -> System.out.println("1");
      case RecordDouble(Number source1) -> System.out.println("1");
      case RecordDouble(Object source1) -> System.out.println("1");
    }
  }

  private static void testForRecordLongObj(RecordLongObj source) {
    switch (source) {
      case RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordLongObj(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'char'">char source1</error>) -> System.out.println("1");//error
      case RecordLongObj(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(long source1) -> System.out.println("1");
      case RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'int'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'int'">int source1</error>) -> System.out.println("1");//error
      case RecordLongObj(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(long source1) -> System.out.println("1");
      case RecordLongObj(Long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(Long source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordLongObj(Long source1)'">RecordLongObj(long source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(float source1) -> System.out.println("1");
      case RecordLongObj(Long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Long'">Integer source1</error>) -> System.out.println("1");//error
      case RecordLongObj(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(long source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordLongObj(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordLongObj(Object source1)'">RecordLongObj(long source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(long source1) -> System.out.println("1");
      case RecordLongObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'int'">int source1</error>) -> System.out.println("1");//error
      case RecordLongObj(Number source1) -> System.out.println("1");
      case RecordLongObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(Long source1) -> System.out.println("1");
      case RecordLongObj(Number source1) -> System.out.println("1");
      case RecordLongObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Long'">Double source1</error>) -> System.out.println("1");//error
      case RecordLongObj(Number source1) -> System.out.println("1");
      case RecordLongObj(Object source1) -> System.out.println("1");
    }
  }

  private static void testForRecordLong(RecordLong source) {
    switch (source) {
      case RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(char source1) -> System.out.println("1");
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(long source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordLong(long source1)'">RecordLong(int source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLong(int source1) -> System.out.println("1");
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(long source1) -> System.out.println("1");
      case RecordLong(Long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(Long source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordLong(Long source1)'">RecordLong(long source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLong(float source1) -> System.out.println("1");
      case RecordLong(Long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Integer'">Integer source1</error>) -> System.out.println("1");//error
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(long source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordLong(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordLong(Object source1)'">RecordLong(long source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLong(long source1) -> System.out.println("1");
      case RecordLong(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(int source1) -> System.out.println("1");
      case RecordLong(Number source1) -> System.out.println("1");
      case RecordLong(Object source1) -> System.out.println("1");
    }
  }

  private static void testForRecordByte(RecordByte source) {
    switch (source) {
      case RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordByte(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case RecordByte(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(Byte source1) -> System.out.println("1");
      case RecordByte(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(int source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordByte(int source1)'">RecordByte(byte source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Integer'">Integer source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Integer'">Integer source1</error>) -> System.out.println("1");//error
      case RecordByte(byte source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(int source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordByte(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordByte(Object source1)'">RecordByte(byte source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case RecordByte(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case <error descr="Incompatible types. Found: 'dfa.SwitchRecordPrimitive.RecordChar', required: 'dfa.SwitchRecordPrimitive.RecordByte'">RecordChar(Object source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case RecordByte(Number source1) -> System.out.println("1");
      case RecordByte(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordByte(Object source1)'">RecordByte(byte source1)</error> -> System.out.println("1");//error
    }
  }

  private static void testForRecordCharObj(RecordCharObj source) {
    switch (source) {
      case RecordCharObj(<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordCharObj(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(char source1) -> System.out.println("1");
      case RecordCharObj(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(Character source1) -> System.out.println("1");
      case RecordCharObj(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(int source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordCharObj(int source1)'">RecordCharObj(char source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(int source1) -> System.out.println("1");
      case RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Character'">Integer source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Character'">Integer source1</error>) -> System.out.println("1");//error
      case RecordCharObj(char source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(int source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordCharObj(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordCharObj(Object source1)'">RecordCharObj(char source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(char source1) -> System.out.println("1");
      case RecordCharObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(int source1) -> System.out.println("1");
      case RecordCharObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(char source1) -> System.out.println("1");
      case RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Character'">Number source1</error>) -> System.out.println("1");//error
      case RecordCharObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(Character source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordCharObj(Object source1)'">RecordCharObj(char source1)</error> -> System.out.println("1");//error
    }
  }

  private static void testForRecordChar(RecordChar source) {
    switch (source) {
      case RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordChar(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(char source1) -> System.out.println("1");
      case RecordChar(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(Character source1) -> System.out.println("1");
      case RecordChar(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(int source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordChar(int source1)'">RecordChar(char source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(int source1) -> System.out.println("1");
      case RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Integer'">Integer source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Integer'">Integer source1</error>) -> System.out.println("1");//error
      case RecordChar(char source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(int source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordChar(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordChar(Object source1)'">RecordChar(char source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(char source1) -> System.out.println("1");
      case RecordChar(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(int source1) -> System.out.println("1");
      case RecordChar(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(int source1) -> System.out.println("1");
      case RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Number'">Number source1</error>) -> System.out.println("1");//error
      case RecordChar(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordChar(Object source1)'">RecordChar(int source1)</error> -> System.out.println("1");//error
    }
  }

  private static void testForRecordInt(RecordInt source) {
    switch (source) {
      case RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordInt(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(char source1) -> System.out.println("1");
      case RecordInt(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Character'">Character source1</error>) -> System.out.println("1");//error
      case RecordInt(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordInt(int source1)'">RecordInt(char source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      case RecordInt(Integer source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(Integer source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordInt(Integer source1)'">RecordInt(int source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordInt(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordInt(Object source1)'">RecordInt(int source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      case RecordInt(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      case RecordInt(Number source1) -> System.out.println("1");
      case RecordInt(Object source1) -> System.out.println("1");
    }
  }

  private static void testForRecordIntObj(RecordIntObj source) {
    switch (source) {
      case RecordIntObj(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'boolean'">boolean source1</error>) -> System.out.println("1");//error
      case RecordIntObj(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordIntObj(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'char'">char source1</error>) -> System.out.println("1");//error
      case RecordIntObj(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Integer'">Character source1</error>) -> System.out.println("1");//error
      case RecordIntObj(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordIntObj(int source1) -> System.out.println("1");
      case RecordIntObj(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'char'">char source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordIntObj(int source1) -> System.out.println("1");
      case RecordIntObj(Integer source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordIntObj(Integer source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordIntObj(Integer source1)'">RecordIntObj(int source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordIntObj(int source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordIntObj(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordIntObj(Object source1)'">RecordIntObj(int source1)</error> -> System.out.println("1");//error
    }
  }

  private static void testForRecordBool(RecordBool source) {
    switch (source) {
      case RecordBool(boolean source1) -> System.out.println("1");
      case RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'int'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordBool(boolean source1) -> System.out.println("1");
      case RecordBool(Boolean source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordBool(Boolean source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordBool(Boolean source1)'">RecordBool(boolean source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordBool(boolean source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordBool(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordBool(Object source1)'">RecordBool(boolean source1)</error> -> System.out.println("1");//error
    }
  }

  private static void testForRecordBoolObj(RecordBoolObj source) {
    switch (source) {
      case RecordBoolObj(boolean source1) -> System.out.println("1");
      case RecordBoolObj(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'int'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordBoolObj(boolean source1) -> System.out.println("1");
      case RecordBoolObj(Boolean source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordBoolObj(Boolean source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordBoolObj(Boolean source1)'">RecordBoolObj(boolean source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordBoolObj(boolean source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordBoolObj(Object source1) -> System.out.println("1");
      case <error descr="Label is dominated by a preceding case label 'RecordBoolObj(Object source1)'">RecordBoolObj(boolean source1)</error> -> System.out.println("1");//error
    }
  }
}
