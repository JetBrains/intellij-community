package dfa;

public class SwitchRecordPrimitiveIsNotAllowed {

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
  }

  private static void testForRecordRecordInt(RecordRecordInt source) {
    switch (source) {
      case RecordRecordInt(RecordInt(<error descr="Incompatible types. Found: 'boolean', required: 'int'">boolean source1</error>)) -> System.out.println("1"); //error
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>)) -> System.out.println("1");//error
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'int'">Character source1</error>)) -> System.out.println("1");//error
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>)) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Integer source1</error>)) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordRecordInt(RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Integer source1</error>)) -> System.out.println("1");//error
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>)) -> System.out.println("1");//error
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>)) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordRecordInt(RecordInt(int source1)) -> System.out.println("1");
      case RecordRecordInt(RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number source1</error>)) -> System.out.println("1");//error
      case RecordRecordInt(RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>)) -> System.out.println("1");//error
    }
  }

  private static void testForRecordNumber(RecordNumber source) {
    switch (source) {
      case RecordNumber(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Number'">boolean source1</error>) -> System.out.println("1");//error
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordNumber(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Number'">char source1</error>) -> System.out.println("1");//error
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordNumber(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Number'">Character source1</error>) -> System.out.println("1");//error
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordNumber(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Number'">char source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordNumber(Integer source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(Integer source1) -> System.out.println("1");
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordNumber(Object source1) -> System.out.println("1");
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordNumber(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordNumber(Number source1) -> System.out.println("1");
      case RecordNumber(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordNumber(Number source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
  }

  private static void testForRecordDoubleObj(RecordDoubleObj source) {
    switch (source) {
      case RecordDoubleObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Double'">boolean source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Double'">char source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">int source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(Double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(Double source1) -> System.out.println("1");
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">float source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(Double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Double'">Integer source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordDoubleObj(Object source1) -> System.out.println("1");
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDoubleObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">int source1</error>) -> System.out.println("1");//error
      case RecordDoubleObj(Number source1) -> System.out.println("1");
      case RecordDoubleObj(Object source1) -> System.out.println("1");
    }
  }

  private static void testForRecordDouble(RecordDouble source) {
    switch (source) {
      case RecordDouble(<error descr="Incompatible types. Found: 'boolean', required: 'double'">boolean source1</error>) -> System.out.println("1");//error
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(double source1) -> System.out.println("1");
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(double source1) -> System.out.println("1");
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Double source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Double source1</error>) -> System.out.println("1");//error
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float source1</error>) -> System.out.println("1");//error
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Double source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'double'">Integer source1</error>) -> System.out.println("1");//error
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(double source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
      case RecordDouble(double source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordDouble(double source1) -> System.out.println("1");
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number source1</error>) -> System.out.println("1");//error
      case RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
  }

  private static void testForRecordLongObj(RecordLongObj source) {
    switch (source) {
      case RecordLongObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Long'">boolean source1</error>) -> System.out.println("1");//error
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Long'">char source1</error>) -> System.out.println("1");//error
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
      case RecordLongObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">int source1</error>) -> System.out.println("1");//error
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
      case RecordLongObj(Long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(Long source1) -> System.out.println("1");
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float source1</error>) -> System.out.println("1");//error
      case RecordLongObj(Long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Long'">Integer source1</error>) -> System.out.println("1");//error
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordLongObj(Object source1) -> System.out.println("1");
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long source1</error>) -> System.out.println("1");//error
      case RecordLongObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLongObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">int source1</error>) -> System.out.println("1");//error
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
      case RecordLong(<error descr="Incompatible types. Found: 'boolean', required: 'long'">boolean source1</error>) -> System.out.println("1");//error
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(long source1) -> System.out.println("1");
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(long source1) -> System.out.println("1");
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Long source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Long source1</error>) -> System.out.println("1");//error
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float source1</error>) -> System.out.println("1");//error
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Long source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLong(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'long'">Integer source1</error>) -> System.out.println("1");//error
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(long source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
      case RecordLong(long source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordLong(long source1) -> System.out.println("1");
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number source1</error>) -> System.out.println("1");//error
      case RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
  }

  private static void testForRecordByte(RecordByte source) {
    switch (source) {
      case RecordByte(<error descr="Incompatible types. Found: 'boolean', required: 'byte'">boolean source1</error>) -> System.out.println("1");//error
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Byte source1</error>) -> System.out.println("1");//error
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordByte(byte source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case RecordByte(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'byte'">Integer source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'byte'">Integer source1</error>) -> System.out.println("1");//error
      case RecordByte(byte source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
      case RecordByte(byte source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case <error descr="Incompatible types. Found: 'dfa.SwitchRecordPrimitiveIsNotAllowed.RecordChar', required: 'dfa.SwitchRecordPrimitiveIsNotAllowed.RecordByte'">RecordChar(Object source1)</error> -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(byte source1) -> System.out.println("1");
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number source1</error>) -> System.out.println("1");//error
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
      case RecordByte(byte source1) -> System.out.println("1");
    }
  }

  private static void testForRecordCharObj(RecordCharObj source) {
    switch (source) {
      case RecordCharObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Character'">boolean source1</error>) -> System.out.println("1");//error
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(Character source1) -> System.out.println("1");
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Character'">Integer source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Character'">Integer source1</error>) -> System.out.println("1");//error
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordCharObj(Object source1) -> System.out.println("1");
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
      case RecordCharObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordCharObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
      case RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Character'">Number source1</error>) -> System.out.println("1");//error
      case RecordCharObj(Object source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(Character source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordCharObj(Object source1) -> System.out.println("1");
      case RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
    }
  }

  private static void testForRecordChar(RecordChar source) {
    switch (source) {
      case RecordChar(<error descr="Incompatible types. Found: 'boolean', required: 'char'">boolean source1</error>) -> System.out.println("1");//error
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(char source1) -> System.out.println("1");
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Character source1</error>) -> System.out.println("1");//error
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordChar(char source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordChar(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'char'">Integer source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'char'">Integer source1</error>) -> System.out.println("1");//error
      case RecordChar(char source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
      case RecordChar(char source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordChar(char source1) -> System.out.println("1");
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordChar(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'char'">Number source1</error>) -> System.out.println("1");//error
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
      case RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
  }

  private static void testForRecordInt(RecordInt source) {
    switch (source) {
      case RecordInt(<error descr="Incompatible types. Found: 'boolean', required: 'int'">boolean source1</error>) -> System.out.println("1");//error
      case RecordInt(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
      case RecordInt(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'int'">Character source1</error>) -> System.out.println("1");//error
      case RecordInt(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      case RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      case RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Integer source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Integer source1</error>) -> System.out.println("1");//error
      case RecordInt(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
      case RecordInt(int source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      case RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordInt(int source1) -> System.out.println("1");
      case RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number source1</error>) -> System.out.println("1");//error
      case RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
    }
  }

  private static void testForRecordIntObj(RecordIntObj source) {
    switch (source) {
      case RecordIntObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Integer'">boolean source1</error>) -> System.out.println("1");//error
      case RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordIntObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">char source1</error>) -> System.out.println("1");//error
      case RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Integer'">Character source1</error>) -> System.out.println("1");//error
      case RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordIntObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">char source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      case RecordIntObj(Integer source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordIntObj(Integer source1) -> System.out.println("1");
      case RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordIntObj(Object source1) -> System.out.println("1");
      case RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int source1</error>) -> System.out.println("1");//error
    }
  }

  private static void testForRecordBool(RecordBool source) {
    switch (source) {
      case RecordBool(boolean source1) -> System.out.println("1");
      case RecordBool(<error descr="Incompatible types. Found: 'int', required: 'boolean'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordBool(boolean source1) -> System.out.println("1");
      case RecordBool(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Boolean source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordBool(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Boolean source1</error>) -> System.out.println("1");//error
      case RecordBool(boolean source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordBool(boolean source1) -> System.out.println("1");
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordBool(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object source1</error>) -> System.out.println("1");//error
      case RecordBool(boolean source1) -> System.out.println("1");
    }
  }

  private static void testForRecordBoolObj(RecordBoolObj source) {
    switch (source) {
      case RecordBoolObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">boolean source1</error>) -> System.out.println("1");//error
      case RecordBoolObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Boolean'">int source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordBoolObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">boolean source1</error>) -> System.out.println("1");//error
      case RecordBoolObj(Boolean source1) -> System.out.println("1");
    }
    switch (source) {
      case RecordBoolObj(Boolean source1) -> System.out.println("1");
      case RecordBoolObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">boolean source1</error>) -> System.out.println("1");//error
    }
    switch (source) {
      case RecordBoolObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">boolean source1</error>) -> System.out.println("1");//error
      default -> System.out.println("2");
    }
    switch (source) {
      case RecordBoolObj(Object source1) -> System.out.println("1");
      case RecordBoolObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">boolean source1</error>) -> System.out.println("1");//error
    }
  }
}
