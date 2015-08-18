// "Generate overloaded constructor with default parameter value" "true"
class Test {
    Test() {
      this(<caret>);
  }

    Test(int ii){}
}