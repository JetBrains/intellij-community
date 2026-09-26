// "Fix all 'Unnecessary call to 'toString()'' problems in file" "true"

class StringBuilderToStringAssign {

  public static final String CONST_STRING_VAL = "Hello";

  static String str() { return ""; }
  static <T> T gen(T a) { return a; }

  static void stringBuilderToStringAssign(StringBuilder sb) {
    String s1 = sb + "Hello";
      /* 2 */
      /* 3 */
      /* 4 */
      String s2 = "Hello" + /* 1 */sb/* 5 */;
    String s3 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42;
      /* 2 */
      /* 3 */
      /* 4 */
      String s4 = "Hello" + /* 1 */sb/* 5 */ + 42;

    final String finalVal = "Hello";
      /* 2 */
      /* 3 */
      /* 4 */
      String s5 = finalVal + /* 1 */sb/* 5 */ + 42;

    String stringVal = "Hello";
      /* 2 */
      /* 3 */
      /* 4 */
      String s611 = /* 1 */sb/* 5 */ + stringVal;
      /* 2 */
      /* 3 */
      /* 4 */
      String s621 = stringVal + /* 1 */sb/* 5 */;
      /* 2 */
      /* 3 */
      /* 4 */
      String s631 = stringVal + /* 1 */sb/* 5 */ + 42;

    int intVal = 42;
    String s612 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + intVal;
    String s622 = intVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s632 = intVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42;

    final int constIntVal = 42;
    String s61 = constIntVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s62 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + constIntVal;
    String s63 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + constIntVal + 42;

      /* 2 */
      /* 3 */
      /* 4 */
      String s71 = CONST_STRING_VAL + /* 1 */sb/* 5 */;
      /* 2 */
      /* 3 */
      /* 4 */
      String s72 = /* 1 */sb/* 5 */ + CONST_STRING_VAL;
      /* 2 */
      /* 3 */
      /* 4 */
      String s73 = CONST_STRING_VAL + /* 1 */sb/* 5 */ + 42;

      /* 2 */
      /* 3 */
      /* 4 */
      String s81 = str() + /* 1 */sb/* 5 */;
      /* 2 */
      /* 3 */
      /* 4 */
      String s82 = /* 1 */sb/* 5 */ + str();
      /* 2 */
      /* 3 */
      /* 4 */
      String s83 = str() + /* 1 */sb/* 5 */ + str();

      /* 2 */
      /* 3 */
      /* 4 */
      String s91 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + /* 1 */sb/* 5 */;
      /* 2 */
      /* 3 */
      /* 4 */
      /* 2 */
      /* 3 */
      /* 4 */
      String s92 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + /* 1 */sb/* 5 */ + /* 1 */sb/* 5 */;

    String s101 = ("Hello" + sb) + sb + sb;
    String s102 = ("Hello" + sb) + sb + sb;
    String s103 = (("Hello" + sb) + sb) + sb;
    String s104 = ("Hello" + sb + (sb.toString() + ((sb))));
    String s105 = ("Hello" + sb + (sb + ((sb) + "Hello")));
    String s106 = ("Hello" + sb + (sb + ((sb.toString()) + 42)));

    String s107 = ((("Hello")) + sb + (sb + ((sb.toString()) + 42)));

      /* 2 */
      /* 3 */
      /* 4 */
      String s181 = gen("Hello") + /* 1 */sb/* 5 */;
      /* 2 */
      /* 3 */
      /* 4 */
      String s182 = /* 1 */sb/* 5 */ + gen("Hello");
      /* 2 */
      /* 3 */
      /* 4 */
      String s183 = gen("Hello") + /* 1 */sb/* 5 */ + gen("Hello");

    String s191 = gen(42) + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s192 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen(42);
    String s193 = gen(42) + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen(42);
  }
}
