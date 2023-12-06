class Comments {

  private final String s = "asdf";

  public void testasdf() throws Exception {
    try {
      String s = "DateHelper.Today()";
      //Comments on stuff
      String x = "DateHelper.Today()";
      String y = "DateHelper.Today()";
    } catch (Exception <caret>e) {
      throw e;
    }

    String t = "";
  }
}