// "Fix all 'Redundant 'String' operation' problems in file" "true"

class Test {
  String foo1(char c) {
    return new String(new char[] { c })<caret>;
  }

  String foo2(Character character) {
    return new String(new char[] { character });
  }

  String foo3(char c) {
    return /*1*/new/*2*/ String/*3*/(/*4*/new/*5*/ char/*6*/[/*7*/]/*8*/ {/*9*/ c/*10*/ }/*11*/)/*12*/;
  }

  String foo4(char c) {
    return new String((((new char[] { (((c))) }))));
  }
}
