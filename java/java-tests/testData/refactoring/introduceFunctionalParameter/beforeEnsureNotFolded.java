class Test {

  {
    performTest(new int[0]);
  }

  private static void performTest(int[] equals) {
    String[] fields = new String[0];

    <selection>System.out.println();
    final String[] indexed = getIndexed(fields, equals);</selection>

    System.out.println(indexed);
  }

  private static String[] getIndexed(String[] fields, int[] indices) {
    return new String[indices.length];
  }

}