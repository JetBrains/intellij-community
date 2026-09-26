public class Test {
  protected void execute() throws Exception {
    veryveryveryveryveryveryveryLongMethodName("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".isEmpty() ? " " +
                                                                                                                  String.join(";", "b", "c") +
                                                                                                                  " " +
                                                                                                                  String.join(";", "d", "e") : "");
  }

  void veryveryveryveryveryveryveryLongMethodName(String parameter) {

  }
}