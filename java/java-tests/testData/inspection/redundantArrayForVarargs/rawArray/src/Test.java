public class Test {
  {
    try {
      String.class.getConstructor(new Class[]{String.class});
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
  }
}