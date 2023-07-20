public class RawArray {
  {
    try {
      String.class.getConstructor(<warning descr="Redundant array creation for calling varargs method">new Class[]</warning>{String.class});
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}