class Test {
  private String myTimer;
    private final String string;

    Test() {
        <caret>string = "";
        myTimer = string;
  }
}