// "Fix all 'Unnecessary call to 'toString()'' problems in file" "true"

class StringBuilderToStringArgs {

  private static final String CONST_STRING_VAL = "Hello";

  static String str() { return ""; }
  static <T> T gen(T a) { return a; }

  static void stringBuilderToStringArgs(StringBuilder sb) {
    System.out.println(/* 1 */sb./* 2 */to<caret>String/* 3 */(/* 4 */)/* 5 */ + "Hello");
    System.out.println("Hello" + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);
    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42);
    System.out.println("Hello" + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42);

    final String finalVal = "Hello";
    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + finalVal);
    System.out.println(finalVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);
    System.out.println(finalVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42);

    String stringVal = "Hello";
    System.out.println(stringVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42);

    int intVal = 42;
    System.out.println(intVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42);

    final int constIntVal = 42;
    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + constIntVal);
    System.out.println(constIntVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);
    System.out.println(constIntVal + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42);

    System.out.println(CONST_STRING_VAL + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);
    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + CONST_STRING_VAL);
    System.out.println(CONST_STRING_VAL + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + 42);

    System.out.println(str() + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);
    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + str());
    System.out.println(str() + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + str());

    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);
    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);

    System.out.println(("Hello" + sb.toString()) + sb + sb.toString());
    System.out.println(("Hello" + sb.toString()) + sb.toString() + sb.toString());
    System.out.println((("Hello" + sb.toString()) + sb.toString()) + sb.toString());
    System.out.println(("Hello" + sb.toString() + (sb.toString() + ((sb.toString())))));
    System.out.println(("Hello" + sb.toString() + (sb.toString() + ((sb.toString()) + "Hello"))));
    System.out.println(("Hello" + sb.toString() + (sb.toString() + ((sb.toString()) + 42))));

    System.out.println((("Hello")) + sb.toString() + (sb.toString() + ((sb.toString()) + 42)));

    System.out.println(gen("Hello") + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);
    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen("Hello"));
    System.out.println(gen("Hello") + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen("Hello"));

    System.out.println(gen(42) + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */);
    System.out.println(/* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen(42));
    System.out.println(gen(42) + /* 1 */sb./* 2 */toString/* 3 */(/* 4 */)/* 5 */ + gen(42));
  }
}
