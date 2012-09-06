class Test {
    private final String string;
    private String myTimer;

    Test() {
        string = "";<caret>
        myTimer = string;
  }
}