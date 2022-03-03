// "Fix all 'Redundant 'String' operation' problems in file" "true"
class X {
  boolean testStrip(String s) {
    return s.isBlank();
  }

  boolean testStripLeading(String s) {
    return s.isBlank();
  }

  boolean testStripTrailing(String s) {
    return s.isBlank();
  }

  boolean testStripWithComments(String s) {
      /*1*/
      /*2*/
      /*3*/
      return s/*4*/./*5*/isBlank/*6*/(/*7*/)/*8*/;
  }
}