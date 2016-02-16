import java.util.List;

class Test {
  void m(Class<List> clazz){
    List<Object[]> data = foo(clazz);
  }

  public static <T> T foo(Class<? super T> clazz) {
    return null;
  }
}