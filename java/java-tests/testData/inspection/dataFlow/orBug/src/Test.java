public class Test {
  public boolean abc(Object o1, Object o2) {
    if (o1 == null || o2 == null) {
      return o1 == o2;
    }
    return false;
  }
}