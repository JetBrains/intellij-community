class Test {
  private String myTimer;
    private final String string;

    Test() {
        string = "";<caret>
        myTimer = string;
  }
}