// "Replace with lambda" "false"
class Test {
  private final int myId;

  private final Runnable myModels = new Runnable() {
    private Comparable<String> myMapping = new Compa<caret>rable<String>() {
      @Override
      public String apply(final String s) {
        return myId;
      }
    };

    public void run() {}
  };

  public Test(final int i) {
    myId = i;
  }
}