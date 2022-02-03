// "Fix all 'Redundant 'String' operation' problems in file" "true"

class Test {
  String foo1(char c) {
    return String.valueOf(new char[]<caret> { c });
  }

  String foo2(Character character) {
    return String.valueOf(new char[] { character });
  }

  String foo3(char c) {
    return /*1*/String/*2*/./*3*/valueOf/*4*/(/*5*/new/*6*/ char/*7*/[/*8*/]/*9*/ {/*10*/ c/*11*/ }/*12*/)/*13*/;
  }

  String foo4(char c) {
    return String.valueOf((((new char[] { (((c))) }))));
  }
}
