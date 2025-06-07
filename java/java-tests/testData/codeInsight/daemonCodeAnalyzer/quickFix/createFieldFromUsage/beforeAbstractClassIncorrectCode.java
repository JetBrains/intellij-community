// "Create field 'x' in 'SampleClass'" "true-preview"

abstract public class SampleClass {

  void bar(int t) {
    this.<caret>x = 1;
  }

  abstract void foo();

  void foo() {
  }


