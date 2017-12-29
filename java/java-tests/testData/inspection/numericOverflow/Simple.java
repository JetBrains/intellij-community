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
    <warning descr="Numeric overflow in expression">f1 = <warning descr="Numeric overflow in expression">FLOAT_MAX_VALUE + FLOAT_MAX_VALUE</warning></warning>;
    <warning descr="Numeric overflow in expression">f1 = <warning descr="Numeric overflow in expression">FLOAT_MAX_VALUE * 2</warning></warning>;
    <warning descr="Numeric overflow in expression">f1 = <warning descr="Numeric overflow in expression">2 / FLOAT_MIN_VALUE</warning></warning>;
    f1 = FLOAT_MIN_VALUE + 1;
    f1 = - FLOAT_MIN_VALUE;
    f1 = -FLOAT_MAX_VALUE;
    System.out.println(f1);

    double d1 = <warning descr="Numeric overflow in expression">DOUBLE_MAX_VALUE - -DOUBLE_MAX_VALUE</warning>;
    d1 = DOUBLE_MAX_VALUE + 1;
    <warning descr="Numeric overflow in expression">d1 = <warning descr="Numeric overflow in expression">2 * DOUBLE_MAX_VALUE</warning></warning>;
    <warning descr="Numeric overflow in expression">d1 = <warning descr="Numeric overflow in expression">2 / DOUBLE_MIN_VALUE</warning></warning>;
    <warning descr="Numeric overflow in expression">d1 = <warning descr="Numeric overflow in expression">2 / 0.0d</warning></warning>;
    <warning descr="Numeric overflow in expression">d1 = <warning descr="Numeric overflow in expression">2 / 0.0</warning></warning>;
    System.out.println(d1);

    int i1 = <warning descr="Numeric overflow in expression">INTEGER_MAX_VALUE + 1</warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">INTEGER_MAX_VALUE - 1 + 2</warning></warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">INTEGER_MAX_VALUE - INTEGER_MIN_VALUE</warning></warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">-INTEGER_MIN_VALUE</warning></warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE - 1</warning></warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE - INTEGER_MAX_VALUE</warning></warning>;
    i1 = INTEGER_MIN_VALUE + INTEGER_MAX_VALUE;
    i1 = - INTEGER_MAX_VALUE;
    i1 = - -INTEGER_MAX_VALUE;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE * -1</warning></warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE * 2</warning></warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">INTEGER_MAX_VALUE * -2</warning></warning>;
    i1 = INTEGER_MAX_VALUE * -1;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">2 / 0</warning></warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">INTEGER_MIN_VALUE / -1</warning></warning>;
    <warning descr="Numeric overflow in expression">i1 = <warning descr="Numeric overflow in expression">1000 << 30</warning></warning>;
    System.out.println(i1);

    long l1 = <warning descr="Numeric overflow in expression">LONG_MAX_VALUE + 1</warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MAX_VALUE - 1 + 2</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MAX_VALUE - LONG_MIN_VALUE</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">-LONG_MIN_VALUE</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE - 1</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE - LONG_MAX_VALUE</warning></warning>;
    l1 = LONG_MIN_VALUE + LONG_MAX_VALUE;
    l1 = - LONG_MAX_VALUE;
    l1 = - -LONG_MAX_VALUE;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">-INTEGER_MIN_VALUE</warning></warning>;
    l1 =  -1L + INTEGER_MIN_VALUE;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE * INTEGER_MIN_VALUE</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE * -1</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE * 2</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MAX_VALUE * -2</warning></warning>;
    l1 = INTEGER_MIN_VALUE * -1L;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">2 / 0L</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">2 % 0L</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">LONG_MIN_VALUE / -1</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">30 * 24 * 60 * 60 * 1000</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression"><warning descr="Numeric overflow in expression">30000000 * 243232323 * <warning descr="Numeric overflow in expression">(<warning descr="Numeric overflow in expression">LONG_MAX_VALUE +3</warning>)</warning></warning> / 5</warning></warning>;
    <warning descr="Numeric overflow in expression">l1 = <warning descr="Numeric overflow in expression">1000 << 62</warning></warning>;
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
}