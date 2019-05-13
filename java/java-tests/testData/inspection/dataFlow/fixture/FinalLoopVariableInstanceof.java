class Test {

  public static void main(Object[] args) throws Exception {
    boolean elvisLives = false;
    for (final Object o : args) {
      if (o instanceof Integer) {
        elvisLives = true;
      } else {
        if (elvisLives) {
          System.err.println("Elvis is alive!");
        }
      }
    }
  }

}
