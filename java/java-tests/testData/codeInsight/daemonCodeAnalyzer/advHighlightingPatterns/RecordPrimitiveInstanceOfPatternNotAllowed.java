public class RecordPrimitiveInstanceOfPatternNotAllowed {

  record RecordBool(boolean source) {
  }

  record RecordInt(int source) {
  }

  record RecordLong(long source) {
  }

  record RecordDouble(double source) {
  }

  record RecordFloat(float source) {
  }

  record RecordByte(byte source) {
  }

  record RecordChar(char source) {
  }

  record RecordShort(short source) {
  }

  record RecordBoolObj(Boolean source) {
  }

  record RecordIntObj(Integer source) {
  }

  record RecordLongObj(Long source) {
  }

  record RecordDoubleObj(Double source) {
  }

  record RecordFloatObj(Float source) {
  }

  record RecordByteObj(Byte source) {
  }

  record RecordCharObj(Character source) {
  }

  record RecordShortObj(Short source) {
  }

  record RecordNumber(Number source) {
  }

  record RecordObject(Object source) {
  }

  record RecordRecordInt(RecordInt source) {
  }

  public static void main(String[] args) {
    testForRecordBool(new RecordBool(true));
    testForRecordInt(new RecordInt(1));
    testForRecordLong(new RecordLong(1L));
    testForRecordDouble(new RecordDouble(2));
    testForRecordFloat(new RecordFloat(1.0f));
    testForRecordByte(new RecordByte((byte) 1));
    testForRecordChar(new RecordChar('1'));
    testForRecordShort(new RecordShort((short) 1));

    testForRecordBoolObject(new RecordBoolObj(true));
    testForRecordIntObject(new RecordIntObj(1));
    testForRecordLongObject(new RecordLongObj(1L));
    testForRecordDoubleObject(new RecordDoubleObj(1.0));
    testForRecordFloatObject(new RecordFloatObj(1.0f));
    testForRecordByteObject(new RecordByteObj((byte) 1));
    testForRecordCharObject(new RecordCharObj('a'));
    testForRecordShortObject(new RecordShortObj((short) 1));

    testForRecordObject(new RecordObject(1));
    testForRecordNumber(new RecordNumber(1));

    testForRecordRecordInt(new RecordRecordInt(new RecordInt(1)));
  }

  private static void testForRecordRecordInt(RecordRecordInt source) {
    if (source instanceof RecordRecordInt(RecordInt(<error descr="Incompatible types. Found: 'boolean', required: 'int'">boolean target</error>))) { } //error
    if (source instanceof RecordRecordInt(RecordInt(int target))) { }
  }

  private static void testForRecordBool(RecordBool source) {
    if (source instanceof RecordBool(boolean target)) { }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'int', required: 'boolean'">int target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'long', required: 'boolean'">long target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'double', required: 'boolean'">double target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'float', required: 'boolean'">float target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'byte', required: 'boolean'">byte target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'char', required: 'boolean'">char target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'short', required: 'boolean'">short target</error>)) { } //error

    if (source instanceof RecordBool(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Boolean target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'boolean'">Integer target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'boolean'">Long target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'boolean'">Double target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'boolean'">Float target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'boolean'">Byte target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'boolean'">Character target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'boolean'">Short target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object target</error>)) { } //error
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'boolean'">Number target</error>)) { } //error
  }

  private static void testForRecordInt(RecordInt source) {
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'boolean', required: 'int'">boolean target</error>)) { } //error
    if (source instanceof RecordInt(int target)) { }
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'int'">Boolean target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Integer target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'int'">Long target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'int'">Double target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'int'">Float target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'int'">Byte target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'int'">Character target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'int'">Short target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object target</error>)) { } //error
    if (source instanceof RecordInt(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number target</error>)) { } //error
  }

  private static void testForRecordLong(RecordLong source) {
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'boolean', required: 'long'">boolean target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordLong(long target)) { }
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'long'">Boolean target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'long'">Integer target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Long target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'long'">Double target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'long'">Float target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'long'">Byte target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'long'">Character target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'long'">Short target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object target</error>)) { } //error
    if (source instanceof RecordLong(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number target</error>)) { } //error
  }

  private static void testForRecordDouble(RecordDouble source) {
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'boolean', required: 'double'">boolean target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordDouble(double target)) { }
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'double'">Boolean target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'double'">Integer target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'double'">Long target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Double target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'double'">Float target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'double'">Byte target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'double'">Character target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'double'">Short target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object target</error>)) { } //error
    if (source instanceof RecordDouble(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number target</error>)) { } //error
  }

  private static void testForRecordFloat(RecordFloat source) {
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'boolean', required: 'float'">boolean target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordFloat(float target)) { }
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'float'">Boolean target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'float'">Integer target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'float'">Long target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'float'">Double target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Float target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'float'">Byte target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'float'">Character target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'float'">Short target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object target</error>)) { } //error
    if (source instanceof RecordFloat(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number target</error>)) { } //error
  }

  private static void testForRecordByte(RecordByte source) {
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'boolean', required: 'byte'">boolean target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordByte(byte target)) { }
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'byte'">Boolean target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'byte'">Integer target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'byte'">Long target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'byte'">Double target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'byte'">Float target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Byte target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'byte'">Character target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'byte'">Short _</error>)) {  //error
      System.out.println();
    }
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object target</error>)) { } //error
    if (source instanceof RecordByte(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number _</error>)) { //error
      System.out.println();
    }
  }

  private static void testForRecordChar(RecordChar source) {
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'boolean', required: 'char'">boolean target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordChar(char target)) { }
    if (source instanceof RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'char'">Boolean target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'char'">Integer target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'char'">Long target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'char'">Double target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'char'">Float target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'char'">Byte target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Character target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'char'">Short target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object target</error>)) { } //error
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'char'">Number target</error>)) { } //error
  }

  private static void testForRecordShort(RecordShort source) {
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'boolean', required: 'short'">boolean target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char target</error>)) { } //error
    if (source instanceof RecordShort(short target)) { }

    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'short'">Boolean target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'short'">Integer target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'short'">Long target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'short'">Double target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'short'">Float target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'short'">Byte target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'short'">Character target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Short target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Object target</error>)) { } //error
    if (source instanceof RecordShort(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">Number target</error>)) { } //error
  }

  private static void testForRecordBoolObject(RecordBoolObj source) {
    if (source instanceof RecordBoolObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">boolean target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Boolean'">int target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Boolean'">long target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Boolean'">double target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Boolean'">float target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Boolean'">byte target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Boolean'">char target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Boolean'">short target</error>)) { } //error

    if (source instanceof RecordBoolObj(Boolean target)) { }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Boolean'">Integer target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Boolean'">Long target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Boolean'">Double target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Boolean'">Float target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Boolean'">Byte target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Boolean'">Character target</error>)) { } //error
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Boolean'">Short target</error>)) { } //error
    if (source instanceof RecordBoolObj(Object target)) { }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Boolean'">Number target</error>)) { } //error
  }

  private static void testForRecordIntObject(RecordIntObj source) {
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Integer'">boolean target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Integer'">byte target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">char target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Integer'">short target</error>)) { } //error

    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Integer'">Boolean target</error>)) { } //error
    if (source instanceof RecordIntObj(Integer target)) { }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Integer'">Long target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Integer'">Double target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Integer'">Float target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Integer'">Byte target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Integer'">Character target</error>)) { } //error
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Integer'">Short target</error>)) { } //error
    if (source instanceof RecordIntObj(Object target)) { }
    if (source instanceof RecordIntObj(Number target)) { }
  }

  private static void testForRecordLongObject(RecordLongObj source) {
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Long'">boolean target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">int target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Long'">byte target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Long'">char target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Long'">short target</error>)) { } //error

    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Long'">Boolean target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Long'">Integer target</error>)) { } //error
    if (source instanceof RecordLongObj(Long target)) { }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Long'">Double target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Long'">Float target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Long'">Byte target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Long'">Character target</error>)) { } //error
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Long'">Short target</error>)) { } //error
    if (source instanceof RecordLongObj(Object target)) { }
    if (source instanceof RecordLongObj(Number target)) { }
  }

  private static void testForRecordDoubleObject(RecordDoubleObj source) {
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Double'">boolean target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">int target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Double'">long target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">float target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Double'">byte target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Double'">char target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Double'">short target</error>)) { } //error

    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Double'">Boolean target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Double'">Integer target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Double'">Long target</error>)) { } //error
    if (source instanceof RecordDoubleObj(Double target)) { }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Double'">Float target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Double'">Byte target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Double'">Character target</error>)) { } //error
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Double'">Short target</error>)) { } //error
    if (source instanceof RecordDoubleObj(Object target)) { }
    if (source instanceof RecordDoubleObj(Number target)) { }
  }

  private static void testForRecordFloatObject(RecordFloatObj source) {
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Float'">boolean target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Float'">int target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Float'">long target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Float'">byte target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Float'">char target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Float'">short target</error>)) { } //error

    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Float'">Boolean target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Float'">Integer target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Float'">Long target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Float'">Double target</error>)) { } //error
    if (source instanceof RecordFloatObj(Float target)) { }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Float'">Byte target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Float'">Character target</error>)) { } //error
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Float'">Short target</error>)) { } //error
    if (source instanceof RecordFloatObj(Object target)) { }
    if (source instanceof RecordFloatObj(Number target)) { }
  }

  private static void testForRecordByteObject(RecordByteObj source) {
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Byte'">boolean target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Byte'">char target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Byte'">Boolean target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Byte'">Integer target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Byte'">Long target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Byte'">Double target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Byte'">Float target</error>)) { } //error
    if (source instanceof RecordByteObj(Byte target)) { }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Byte'">Character target</error>)) { } //error
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Byte'">Short target</error>)) { } //error
    if (source instanceof RecordByteObj(Object target)) { }
    if (source instanceof RecordByteObj(Number target)) { }
  }

  private static void testForRecordCharObject(RecordCharObj source) {
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Character'">boolean target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Character'">byte target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Character'">short target</error>)) { } //error

    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Character'">Boolean target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Character'">Integer target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Character'">Long target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Character'">Double target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Character'">Float target</error>)) { } //error
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Character'">Byte target</error>)) { } //error
    if (source instanceof RecordCharObj(Character target)) { }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Character'">Short target</error>)) { } //error
    if (source instanceof RecordCharObj(Object target)) { }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Character'">Number target</error>)) { } //error
  }

  private static void testForRecordShortObject(RecordShortObj source) {
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Short'">boolean target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Short'">byte target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Short'">char target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Short'">Boolean target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Short'">Integer target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Short'">Long target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Short'">Double target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Short'">Float target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Short'">Byte target</error>)) { } //error
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Short'">Character target</error>)) { } //error
    if (source instanceof RecordShortObj(Short target)) { }
    if (source instanceof RecordShortObj(Object target)) { }
    if (source instanceof RecordShortObj(Number target)) { }
  }

  private static void testForRecordObject(RecordObject source) {
    if (source instanceof RecordObject(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">boolean target</error>)) { } //error
    if (source instanceof RecordObject(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordObject(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordObject(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordObject(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordObject(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordObject(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">char target</error>)) { } //error
    if (source instanceof RecordObject(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordObject(Boolean target)) { }
    if (source instanceof RecordObject(Integer target)) { }
    if (source instanceof RecordObject(Long target)) { }
    if (source instanceof RecordObject(Double target)) { }
    if (source instanceof RecordObject(Float target)) { }
    if (source instanceof RecordObject(Byte target)) { }
    if (source instanceof RecordObject(Character target)) { }
    if (source instanceof RecordObject(Short target)) { }
    if (source instanceof RecordObject(Object target)) { }
    if (source instanceof RecordObject(Number target)) { }
  }

  private static void testForRecordNumber(RecordNumber source) {
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Number'">boolean target</error>)) { } //error
    if (source instanceof RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">int target</error>)) { } //error
    if (source instanceof RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">long target</error>)) { } //error
    if (source instanceof RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">double target</error>)) { } //error
    if (source instanceof RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">float target</error>)) { } //error
    if (source instanceof RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">byte target</error>)) { } //error
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Number'">char target</error>)) { } //error
    if (source instanceof RecordNumber(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">short target</error>)) { } //error

    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Number'">Boolean target</error>)) { } //error
    if (source instanceof RecordNumber(Integer target)) { }
    if (source instanceof RecordNumber(Long target)) { }
    if (source instanceof RecordNumber(Double target)) { }
    if (source instanceof RecordNumber(Float target)) { }
    if (source instanceof RecordNumber(Byte target)) { }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Number'">Character target</error>)) { } //error
    if (source instanceof RecordNumber(Short target)) { }
    if (source instanceof RecordNumber(Object target)) { }
    if (source instanceof RecordNumber(Number target)) { }
  }
}
