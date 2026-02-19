// "Remove unnecessary 'this' qualifier" "true-preview"
public class Outer {
  class Inner { }
  void test() {
    Inner inner = /*0*/(/*1*/Outer./*2*/th<caret>is/*3*/)/*4*/./*5*/new Inner();
  }
}