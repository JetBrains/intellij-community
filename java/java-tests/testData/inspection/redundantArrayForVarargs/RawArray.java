public class RawArray {
  {
    try {
      String.class.getConstructor(<warning descr="Redundant array creation for calling varargs method">new Class[]{String.class}</warning>);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}