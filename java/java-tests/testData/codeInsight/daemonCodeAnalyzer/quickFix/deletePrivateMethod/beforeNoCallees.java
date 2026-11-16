// "Delete method 'test()'|->… and nothing else" "false"
class X {
  private void t<caret>est() {
    System.out.println("Hello");
  }
}