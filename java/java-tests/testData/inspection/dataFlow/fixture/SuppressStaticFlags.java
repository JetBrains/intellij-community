public class SuppressStaticFlags {
  private static final boolean DEBUG = true;
  private static final int DEBUG_LEVEL = 2;

  private static final int FLAGS = 0x03;
  private static final int FLAG1 = 0x01;
  private static final int FLAG2 = 0x02;

  private static final long LONG_FLAGS = 0x03;
  private static final long LONG_FLAG1 = 0x01;
  private static final long LONG_FLAG2 = 0x02;

  public void testDebug(String s) {
    if(DEBUG) {
      System.out.println("Debug is on");
    }
    if(DEBUG && s.length() == 0) {
      System.out.println("String is empty");
    }
  }

  public void testDebugLevel(String s) {
    if(DEBUG_LEVEL == 3) {
      System.out.println("Debug level is high");
    }
    if(DEBUG_LEVEL > 1 && s.length() == 0) {
      System.out.println("String is empty");
    }
  }

  public void testFlags(String s) {
    if((FLAGS & 1) == 0) {
      System.out.println("No flag");
    }
    if(((FLAGS) & (FLAG2)) != 0 && s.length() == 0) {
      System.out.println("Flag2");
    }
    if(<warning descr="Condition '(3 & 1) == 2' is always 'false'">(3 & 1) == 2</warning>) {
      System.out.println("Literals");
    }
  }

  public void testLongFlags(String s) {
    if((LONG_FLAGS & 1) == 0) {
      System.out.println("No flag");
    }
    if(((LONG_FLAGS) & (LONG_FLAG2)) != 0 && s == null) {
      System.out.println("Flag2");
    }
    if(<warning descr="Condition '(3L & 1L) == 2L' is always 'false'">(3L & 1L) == 2L</warning>) {
      System.out.println("Literals");
    }
  }



  public static final boolean nonCompileTimeConstant = new java.util.Random().nextBoolean();

  public void hello1(Object file) {
    if (file == null) {
      return;
    }
    if (<warning descr="Condition 'file == null && nonCompileTimeConstant' is always 'false'"><warning descr="Condition 'file == null' is always 'false'">file == null</warning> && nonCompileTimeConstant</warning>) {
      return;
    }
    System.out.println("hello");
  }

}