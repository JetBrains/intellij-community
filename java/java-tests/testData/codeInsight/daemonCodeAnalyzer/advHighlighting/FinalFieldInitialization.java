
class TestStaticBlockFinalInitializer {

  public static final String BAR;
  public static final String XYZ_BEFORE;
  public static final String XYZ_BEFORE_1 = <error descr="Variable 'BAR' might not have been initialized">BAR</error>;

  static {
    XYZ_BEFORE = <error descr="Variable 'BAR' might not have been initialized">BAR</error>;
  }

  public static final String BAZ = (BAR =  "Test");

  public static final String XYZ_AFTER;
  public static final String XYZ_AFTER_1 = BAR;

  static {
    XYZ_AFTER = BAR;
  }

}
class Initialize {
  final int a;
  final int b;
  final int c;

  {
    c = 3;
    System.out.println(c.<error descr="Identifier expected">)</error>;
  }

  Initialize () {
    a = 1;
    b = 2;
  }
}

