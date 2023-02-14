// "Introduce new StringBuilder to update variable 'res' (null-safe)" "true-preview"
public class SB {
  public static void main(String[] args) {
      StringBuilder resBuilder = null;
      for (String arg : args) {
      if (resBuilder == null) {
        resBuilder = new StringBuilder("[");
      } else {
        resBuilder.append(",");
      }
      resBuilder.append(arg);
    }
      String res = String.valueOf(resBuilder);
      res += "]";
    System.out.println(res);
  }
}

