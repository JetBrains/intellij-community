// "Use 'isBlank()' and remove redundant 'strip()' call" "true"
class X {
  boolean testStrip(String s) {
    return s.isBlank();
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