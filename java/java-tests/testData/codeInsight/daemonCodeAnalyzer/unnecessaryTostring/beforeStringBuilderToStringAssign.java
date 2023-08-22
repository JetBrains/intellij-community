// "Fix all 'Unnecessary call to 'toString()'' problems in file" "true"

class StringBuilderToStringAssign {

  public static final String CONST_STRING_VAL = "Hello";

  static String str() { return ""; }
  static <T> T gen(T a) { return a; }

  static void stringBuilderToStringAssign(StringBuilder sb) {
    String s1 = sb.<caret>toString() + "Hello";
    String s2 = "Hello" + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s3 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42;
    String s4 = "Hello" + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42;

    final String finalVal = "Hello";
    String s5 = finalVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42;

    String stringVal = "Hello";
    String s611 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + stringVal;
    String s621 = stringVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s631 = stringVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42;

    int intVal = 42;
    String s612 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + intVal;
    String s622 = intVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s632 = intVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42;

    final int constIntVal = 42;
    String s61 = constIntVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s62 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + constIntVal;
    String s63 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + constIntVal + 42;

    String s71 = CONST_STRING_VAL + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s72 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + CONST_STRING_VAL;
    String s73 = CONST_STRING_VAL + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42;

    String s81 = str() + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s82 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + str();
    String s83 = str() + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + str();

    String s91 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s92 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;

    String s101 = ("Hello" + sb.toString()) + sb + sb.toString();
    String s102 = ("Hello" + sb.toString()) + sb.toString() + sb.toString();
    String s103 = (("Hello" + sb.toString()) + sb.toString()) + sb.toString();
    String s104 = ("Hello" + sb.toString() + (sb.toString() + ((sb.toString()))));
    String s105 = ("Hello" + sb.toString() + (sb.toString() + ((sb.toString()) + "Hello")));
    String s106 = ("Hello" + sb.toString() + (sb.toString() + ((sb.toString()) + 42)));

    String s107 = ((("Hello")) + sb.toString() + (sb.toString() + ((sb.toString()) + 42)));

    String s181 = gen("Hello") + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s182 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen("Hello");
    String s183 = gen("Hello") + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen("Hello");

    String s191 = gen(42) + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */;
    String s192 = /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen(42);
    String s193 = gen(42) + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen(42);
  }
}
