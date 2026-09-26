// "Replace with record pattern" "false"
class X {
  record EmptyBox() {
  }

  void Test(Object obj) {
    if (obj instanceof Emp<caret>tyBox emptyBox) {
      System.out.println("Fill it up and send it back");
    }
  }
}
