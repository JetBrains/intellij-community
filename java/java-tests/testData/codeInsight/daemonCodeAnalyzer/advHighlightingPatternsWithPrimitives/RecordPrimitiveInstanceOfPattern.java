public class RecordPrimitiveInstanceOfPattern {

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
    if (source instanceof RecordRecordInt(RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'boolean'">boolean target</error>))) { //error
      System.out.println(target);
    }
    if (source instanceof RecordRecordInt(RecordInt(int target))) {
      System.out.println(target);
    }
  }

  private static void testForRecordBool(RecordBool source) {
    if (source instanceof RecordBool(boolean target)) {
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'int'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'long'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'double'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'float'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'short'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordBool(Boolean target)) {
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Integer'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Long'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Double'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Float'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Byte'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Character'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Short'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Number'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordInt(RecordInt source) {
    if (source instanceof RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Boolean'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(Integer target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Long'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Double'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Float'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Byte'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Character'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Short'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordLong(RecordLong source) {
    if (source instanceof RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Boolean'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Integer'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(Long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Double'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Float'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Byte'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Character'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Short'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordDouble(RecordDouble source) {
    if (source instanceof RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Boolean'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Integer'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Long'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(Double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Float'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Byte'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Character'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Short'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordFloat(RecordFloat source) {
    if (source instanceof RecordFloat(<error descr="Inconvertible types; cannot cast 'float' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordFloat(<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Boolean'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Integer'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Long'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Double'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(Float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Byte'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Character'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Short'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordByte(RecordByte source) {
    if (source instanceof RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Boolean'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Integer'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Long'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Double'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Float'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(Byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Character'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Short'">Short _</error>)) { //error
      System.out.println();
    }
    if (source instanceof RecordByte(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(Number _)) {
      System.out.println();
    }
  }

  private static void testForRecordChar(RecordChar source) {
    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Boolean'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Integer'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Long'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Double'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Float'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Byte'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(Character target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Short'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Number'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordShort(RecordShort source) {
    if (source instanceof RecordShort(<error descr="Inconvertible types; cannot cast 'short' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShort(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShort(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShort(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShort(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShort(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShort(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordShort(<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Boolean'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Integer'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Long'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Double'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Float'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Byte'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Character'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(Short target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShort(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShort(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordBoolObject(RecordBoolObj source) {
    if (source instanceof RecordBoolObj(boolean target)) {
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'int'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'long'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'double'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'float'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'short'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordBoolObj(Boolean target)) {
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Boolean'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Boolean'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Boolean'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Boolean'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Boolean'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Boolean'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Boolean'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Boolean'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordIntObject(RecordIntObj source) {
    if (source instanceof RecordIntObj(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'short'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Integer'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(Integer target)) {
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Integer'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Integer'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Integer'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Integer'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Integer'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Integer'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordLongObject(RecordLongObj source) {
    if (source instanceof RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'int'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'short'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Long'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Long'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(Long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Long'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Long'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Long'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Long'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Long'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordDoubleObject(RecordDoubleObj source) {
    if (source instanceof RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'int'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'long'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'float'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'short'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Double'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Double'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Double'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(Double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Double'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Double'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Double'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Double'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordFloatObject(RecordFloatObj source) {
    if (source instanceof RecordFloatObj(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'int'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'long'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'short'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Float'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Float'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Float'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Float'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(Float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Float'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Float'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Float'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordByteObject(RecordByteObj source) {
    if (source instanceof RecordByteObj(<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Byte'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Byte'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Byte'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Byte'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Byte'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(Byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Byte'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Byte'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordCharObject(RecordCharObj source) {
    if (source instanceof RecordCharObj(<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'short'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Character'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Character'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Character'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Character'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Character'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Character'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(Character target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Character'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Character'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordShortObject(RecordShortObj source) {
    if (source instanceof RecordShortObj(<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Short'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Short'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Short'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Short'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Short'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Short'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Short'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(Short target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordObject(RecordObject source) {
    if (source instanceof RecordObject(boolean target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordObject(Boolean target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Integer target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Character target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Short target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordObject(Number target)) {
      System.out.println(target);
    }
  }

  private static void testForRecordNumber(RecordNumber source) {
    if (source instanceof RecordNumber(<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'char'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Number'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(Integer target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(Long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(Double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(Float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(Byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Number'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(Short target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordNumber(Number target)) {
      System.out.println(target);
    }
  }
}
