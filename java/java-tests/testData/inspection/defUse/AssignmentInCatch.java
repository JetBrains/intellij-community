class Main {
  static int test() {
    String msg = "ERR1";
    try {
      msg = "ERR2"; // HERE
      return throwsNFE();
    } catch (NumberFormatException e) {
      System.err.println(msg);
    }
    return 0;
  }

  static int throwsNFE() throws NumberFormatException {
    throw new NumberFormatException("NFE");
  }
}