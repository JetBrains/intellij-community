// "Fix all 'Redundant 'String' operation' problems in file" "true"
class X {
  boolean testStrip(String s) {
    return s.st<caret>rip().isEmpty();
  }

  boolean testStripLeading(String s) {
    return s.stripLeading().isEmpty();
  }

  boolean testStripTrailing(String s) {
    return s.stripTrailing().isEmpty();
  }

  boolean testStripWithComments(String s) {
    return s./*1*/strip/*2*/(/*3*/)/*4*/./*5*/isEmpty/*6*/(/*7*/)/*8*/;
  }
}