// "Create field 'x' in 'SampleClass'" "false"

abstract public class SampleClass {

  void bar(int t) {
    this.<caret>x = 1;
  }

  abstract void foo();

  void foo() {
  }


