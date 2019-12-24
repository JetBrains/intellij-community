public class ClassWithConstants {
  public static final int INT_CONST1 = 1;
  public static final int INT_CONST2 = -1;
  public static final int INT_CONST3 = -2147483648;
  public static final long LONG_CONST1 = 2;
  public static final long LONG_CONST2 = 1000000000000L;
  public static final long LONG_CONST3 = -9223372036854775808L;
  public static final short SHORT_CONST = 3;
  public static final byte BYTE_CONST = 4;
  public static final char CHAR_CONST = '5';
  public static final boolean BOOL_CONST = true;
  public static final float FLOAT_CONST = 1.234f;
  public static final double DOUBLE_CONST = 3.456;
  public static final java.lang.String STRING_CONST = "a\r\n\"bcd";
  public static final java.lang.String STRING_EXPRESSION_CONST1 = "a" + "b";
  public static final java.lang.String STRING_EXPRESSION_CONST2 = "a" + 123;
  public static final java.lang.String STRING_EXPRESSION_CONST3 = 123 + "b";
  public static final java.lang.String STRING_EXPRESSION_CONST4 = INT_CONST1 + "aaa";

  public static final java.lang.String STRING_EXPRESSION_CLASS = Integer.class + "xxx";
  public static final java.lang.String STRING_EXPRESSION_CLASS_ARRAY = Integer[].class + "xxx";
  public static final java.lang.String STRING_EXPRESSION_INTERFACE = Runnable.class + "xxx";
  public static final java.lang.String STRING_EXPRESSION_PRIMITIVE = int.class + "xxx";
  public static final java.lang.String STRING_EXPRESSION_PRIMITIVE_ARRAY = int[].class + "xxx";
  public static final java.lang.String STRING_EXPRESSION_METHOD = val() + "xxx";

  public static final double d1 = Double.POSITIVE_INFINITY;
  public static final double d2 = Double.NEGATIVE_INFINITY;
  public static final double d3 = Double.NaN;

  public static int val() {
    return 1;
  }
}
