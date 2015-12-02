import java.util.List;

abstract class Test {

  public static void foo(List<? extends String[]> aClass) {
    copyOfRange(aClass);
  }

  public static <P> void copyOfRange(List<? extends P[]> newType) {}
}
