// "Generate overloaded constructor with default parameter values" "true"
class Test {
    Test() {
      this(<caret>);
  }

    Test(int ii){}
}