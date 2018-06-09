// overflows in const expressions
class c {
  // @*#$& ! references do not work in JRE
  static final long LONG_MIN_VALUE = 0x8000000000000000L;
  static final long LONG_MAX_VALUE = 0x7fffffffffffffffL;
  static final int INTEGER_MIN_VALUE = 0x80000000;
  static final int INTEGER_MAX_VALUE = 0x7fffffff;
  static final float FLOAT_MIN_VALUE = 1.4e-45f;
  static final float FLOAT_MAX_VALUE = 3.4028235e+38f;
  static final double DOUBLE_MIN_VALUE = 4.9e-324;
  static final double DOUBLE_MAX_VALUE = 1.7976931348623157e+308;

  void f() {
    float f1 = <warning descr="Numeric overflow in expression">1.0f / 0.0f</warning>;
    f1 = <warning descr="Numeric overflow in expression">FLOAT_MAX_VALUE + FLOAT_MAX_VALUE</warning>;
    f1 = <warning descr="Numeric overflow in expression">FLOAT_MAX_VALUE * 2</warning>;
    f1 = <warning descr="Numeric overflow in expression">2 / FLOAT_MIN_VALUE</warning>;
    f1 = FLOAT_MIN_VALUE + 1;
    f1 = - FLOAT_MIN_VALUE;
    f1 = -FLOAT_MAX_VALUE;
    System.out.println(f1);

    double d1 = <warning descr="Numeric overflow in expression">DOUBLE_MAX_VALUE - -DOUBLE_MAX_VALUE</warning>;
    d1 = DOUBLE_MAX_VALUE + 1;
    d1 = <warning descr="Numeric overflow in expression">2 * DOUBLE_MAX_VALUE</warning>;
    d1 = <warning descr="Numeric overflow in expression">2 / DOUBLE_MIN_VALUE</warning>;
    d1 = <warning descr="Numeric overflow in expression">2 / 0.0d</warning>;
    d1 = <warning descr="Numeric overflow in expression">2 / 0.0</warning>;
    System.out.println(d1);

    int i1 = <warning descr="Numeric overflow in expression">INTEGER_MAX_VALUE + 1</warning>;
    i1 = <warning descr="Numeric overflow in expression">INTEGER_MAX_VALUE - 1 + 2</warning>;
    i1 = <warning descr="Numeric overflow in expression">INTEGER_MAX_VALUE - INTEGER_MIN_VALUE</warning>;
    i1 = <warning descr="Numeric overflow in expression">-INTEGER_MIN_VALUE</warning>;
    i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE - 1</warning>;
    i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE - INTEGER_MAX_VALUE</warning>;
    i1 = INTEGER_MIN_VALUE + INTEGER_MAX_VALUE;
    i1 = - INTEGER_MAX_VALUE;
    i1 = - -INTEGER_MAX_VALUE;
    i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE * -1</warning>;
    i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE * 2</warning>;
    i1 = <warning descr="Numeric overflow in expression">INTEGER_MAX_VALUE * -2</warning>;
    i1 = INTEGER_MAX_VALUE * -1;
    i1 = <warning descr="Numeric overflow in expression">2 / 0</warning>;
    i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE / -1</warning>;
    i1 = <warning descr="Numeric overflow in expression">1000 << 30</warning>;
    System.out.println(i1);

    long l1 = <warning descr="Numeric overflow in expression">LONG_MAX_VALUE + 1</warning>;
    l1 = <warning descr="Numeric overflow in expression">LONG_MAX_VALUE - 1 + 2</warning>;
    l1 = <warning descr="Numeric overflow in expression">LONG_MAX_VALUE - LONG_MIN_VALUE</warning>;
    l1 = <warning descr="Numeric overflow in expression">-LONG_MIN_VALUE</warning>;
    l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE - 1</warning>;
    l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE - LONG_MAX_VALUE</warning>;
    l1 = LONG_MIN_VALUE + LONG_MAX_VALUE;
    l1 = - LONG_MAX_VALUE;
    l1 = - -LONG_MAX_VALUE;
    l1 = <warning descr="Numeric overflow in expression">-INTEGER_MIN_VALUE</warning>;
    l1 =  -1L + INTEGER_MIN_VALUE;
    l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE * INTEGER_MIN_VALUE</warning>;
    l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE * -1</warning>;
    l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE * 2</warning>;
    l1 = <warning descr="Numeric overflow in expression">LONG_MAX_VALUE * -2</warning>;
    l1 = INTEGER_MIN_VALUE * -1L;
    l1 = <warning descr="Numeric overflow in expression">2 / 0L</warning>;
    l1 = <warning descr="Numeric overflow in expression">2 % 0L</warning>;
    l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE / -1</warning>;
    l1 = <warning descr="Numeric overflow in expression">30 * 24 * 60 * 60 * 1000</warning>;
    l1 = 30000000 * 243232323 * (<warning descr="Numeric overflow in expression">LONG_MAX_VALUE +3</warning>) / 5;
    l1 = <warning descr="Numeric overflow in expression">1000 << 62</warning>;
    System.out.println(l1);
  }

  private static final long MILLIS_PER_DAY = 24 * 3600 * 1000;
  private static final long _7DAYS = 7 * MILLIS_PER_DAY;
  private static final long _30DAYS = 30 * MILLIS_PER_DAY;
  private static final long _1000DAYS = 1000 * MILLIS_PER_DAY;
  {
    System.out.println(_1000DAYS + _30DAYS + _7DAYS);
  }

  int iii = <warning descr="Numeric overflow in expression">2 % 0</warning>;

  double x = -Double.POSITIVE_INFINITY;

  public void checkDonotOverlap(long lower) {
      final long upper =  lower + <warning descr="Numeric overflow in expression">1000 * 31536000</warning>;
  }

  int shiftNeg = 1 << 31;
  int shiftOverflow = <warning descr="Numeric overflow in expression">2 << 31</warning>;
  long shiftLongNeg = 1L << 63;
  long shiftLongOk = 2L << 31;
  long shiftLongOverflow = <warning descr="Numeric overflow in expression">2L << 63</warning>;
}