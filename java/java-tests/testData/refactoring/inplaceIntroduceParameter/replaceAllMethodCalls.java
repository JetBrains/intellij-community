public class Aaa {
  public String method() {
    return get<caret>String() + getString() + getString();
  }

  public String getString() {
    return "123";
  }
}