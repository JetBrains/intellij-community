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
    if (source instanceof RecordRecordInt(RecordInt(<error descr="Incompatible types. Found: 'boolean', required: 'int'">boolean target</error>))) { //error
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
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'int', required: 'boolean'">int target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'long', required: 'boolean'">long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'double', required: 'boolean'">double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'float', required: 'boolean'">float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'byte', required: 'boolean'">byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'char', required: 'boolean'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'short', required: 'boolean'">short target</error>)) {  //error
      System.out.println(target);
    }

    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'boolean'">Boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'boolean'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'boolean'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'boolean'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'boolean'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'boolean'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'boolean'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'boolean'">Short target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'boolean'">Object target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBool(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'boolean'">Number target</error>)) {  //error
      System.out.println(target);
    }
  }

  private static void testForRecordInt(RecordInt source) {
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'boolean', required: 'int'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(int target)) {
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'long', required: 'int'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'double', required: 'int'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'float', required: 'int'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'byte', required: 'int'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'char', required: 'int'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'short', required: 'int'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'int'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'int'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'int'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'int'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'int'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'int'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'int'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'int'">Short target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'int'">Object target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordInt(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'int'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordLong(RecordLong source) {
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'boolean', required: 'long'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'int', required: 'long'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'double', required: 'long'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'float', required: 'long'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'byte', required: 'long'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'char', required: 'long'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'short', required: 'long'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'long'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'long'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'long'">Long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'long'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'long'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'long'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'long'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'long'">Short target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'long'">Object target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLong(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'long'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordDouble(RecordDouble source) {
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'boolean', required: 'double'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'int', required: 'double'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'long', required: 'double'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'float', required: 'double'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'byte', required: 'double'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'char', required: 'double'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'short', required: 'double'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'double'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'double'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'double'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'double'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'double'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'double'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'double'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'double'">Short target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'double'">Object target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDouble(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'double'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordFloat(RecordFloat source) {
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'boolean', required: 'float'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'int', required: 'float'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'long', required: 'float'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'double', required: 'float'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'byte', required: 'float'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'char', required: 'float'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'short', required: 'float'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'float'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'float'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'float'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'float'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'float'">Float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'float'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'float'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'float'">Short target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'float'">Object target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloat(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'float'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordByte(RecordByte source) {
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'boolean', required: 'byte'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'int', required: 'byte'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'long', required: 'byte'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'double', required: 'byte'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'float', required: 'byte'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'char', required: 'byte'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'short', required: 'byte'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'byte'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'byte'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'byte'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'byte'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'byte'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'byte'">Byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'byte'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'byte'">Short _</error>)) {  //error
      System.out.println();
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'byte'">Object target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByte(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'byte'">Number _</error>)) { //error
      System.out.println();
    }
  }

  private static void testForRecordChar(RecordChar source) {
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'boolean', required: 'char'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'int', required: 'char'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'long', required: 'char'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'double', required: 'char'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'float', required: 'char'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'byte', required: 'char'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(char target)) {
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'short', required: 'char'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'char'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'char'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'char'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'char'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'char'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'char'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'char'">Character target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'char'">Short target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'char'">Object target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordChar(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'char'">Number target</error>)) {  //error
      System.out.println(target);
    }
  }

  private static void testForRecordShort(RecordShort source) {
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'boolean', required: 'short'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'int', required: 'short'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'long', required: 'short'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'double', required: 'short'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'float', required: 'short'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'byte', required: 'short'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'char', required: 'short'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(short target)) {
      System.out.println(target);
    }

    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'short'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'short'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'short'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'short'">Double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'short'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'short'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'short'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'short'">Short target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'short'">Object target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShort(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'short'">Number target</error>)) { //error
      System.out.println(target);
    }
  }

  private static void testForRecordBoolObject(RecordBoolObj source) {
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Boolean'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Boolean'">int target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Boolean'">long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Boolean'">double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Boolean'">float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Boolean'">byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Boolean'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Boolean'">short target</error>)) {  //error
      System.out.println(target);
    }

    if (source instanceof RecordBoolObj(Boolean target)) {
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Boolean'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Boolean'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Boolean'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Boolean'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Boolean'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Boolean'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Boolean'">Short target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordBoolObj(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Boolean'">Number target</error>)) {  //error
      System.out.println(target);
    }
  }

  private static void testForRecordIntObject(RecordIntObj source) {
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Integer'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Integer'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Integer'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Integer'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Integer'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Integer'">byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Integer'">short target</error>)) {  //error
      System.out.println(target);
    }

    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Integer'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(Integer target)) {
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Integer'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Integer'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Integer'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Integer'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Integer'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordIntObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Integer'">Short target</error>)) {  //error
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
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Long'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Long'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Long'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Long'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Long'">byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Long'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Long'">short target</error>)) {  //error
      System.out.println(target);
    }

    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Long'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Long'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(Long target)) {
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Long'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Long'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Long'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordLongObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Long'">Character target</error>)) {  //error
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
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Double'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">int target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Double'">long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Double'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Double'">byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Double'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Double'">short target</error>)) {  //error
      System.out.println(target);
    }

    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Double'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Double'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Double'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(Double target)) {
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Double'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Double'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Double'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordDoubleObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Double'">Short target</error>)) {  //error
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
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Float'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Float'">int target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Float'">long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Float'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Float'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Float'">byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Float'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Float'">short target</error>)) {  //error
      System.out.println(target);
    }

    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Float'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Float'">Integer target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Float'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Float'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(Float target)) {
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Float'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Float'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordFloatObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Float'">Short target</error>)) {  //error
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
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Byte'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Byte'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Byte'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Byte'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Byte'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Byte'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Byte'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Byte'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Byte'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Byte'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Byte'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Byte'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Byte'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(Byte target)) {
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Byte'">Character target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordByteObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Byte'">Short target</error>)) {  //error
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
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Character'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Character'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Character'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Character'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Character'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Character'">byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Character'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Character'">short target</error>)) {  //error
      System.out.println(target);
    }

    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Character'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Character'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Character'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Character'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Character'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Character'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(Character target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Short', required: 'java.lang.Character'">Short target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(Object target)) {
      System.out.println(target);
    }
    if (source instanceof RecordCharObj(<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Character'">Number target</error>)) {  //error
      System.out.println(target);
    }
  }

  private static void testForRecordShortObject(RecordShortObj source) {
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Short'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Short'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Short'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Short'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Short'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Short'">byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Short'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Short'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Short'">Boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.Short'">Integer target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Long', required: 'java.lang.Short'">Long target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Double', required: 'java.lang.Short'">Double target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Float', required: 'java.lang.Short'">Float target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Byte', required: 'java.lang.Short'">Byte target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordShortObj(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Short'">Character target</error>)) {  //error
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
    if (source instanceof RecordObject(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Object'">boolean target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordObject(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Object'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordObject(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Object'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordObject(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Object'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordObject(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Object'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordObject(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Object'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordObject(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Object'">char target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordObject(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Object'">short target</error>)) { //error
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
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Number'">boolean target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Number'">int target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'long', required: 'java.lang.Number'">long target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'double', required: 'java.lang.Number'">double target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'float', required: 'java.lang.Number'">float target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'byte', required: 'java.lang.Number'">byte target</error>)) { //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'char', required: 'java.lang.Number'">char target</error>)) {  //error
      System.out.println(target);
    }
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'short', required: 'java.lang.Number'">short target</error>)) { //error
      System.out.println(target);
    }

    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'java.lang.Number'">Boolean target</error>)) {  //error
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
    if (source instanceof RecordNumber(<error descr="Incompatible types. Found: 'java.lang.Character', required: 'java.lang.Number'">Character target</error>)) {  //error
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
