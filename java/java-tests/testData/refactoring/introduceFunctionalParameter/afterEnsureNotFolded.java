import java.util.function.Function;

class Test {

  {
      final int[] equals = new int[0];
      performTest(new Function<String[],String[]>() {
          public String[] apply(String[] fields) {
              System.out.println();
              return getIndexed(fields, equals);
          }
      });
  }

  private static void performTest(Function<String[], String[]> anObject) {
      String[] fields = new String[0];

      final String[] indexed = anObject.apply(fields);

      System.out.println(indexed);
  }

  private static String[] getIndexed(String[] fields, int[] indices) {
    return new String[indices.length];
  }

}