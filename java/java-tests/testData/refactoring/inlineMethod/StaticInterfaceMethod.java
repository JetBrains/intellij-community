interface AccountModule {
  static void createAccount(String a, String b) {
  }

  default void createAccount(String a) {
    createAccount(a, getB());
  }

  String getB();
}
class UseModule implements AccountModule {

  public void createSome() {
    <caret>createAccount("");
  }

  @Override
  public String getB() {
    return "";
  }
}