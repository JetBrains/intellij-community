// "Fix all 'Redundant 'String' operation' problems in file" "true"

class Test {
  String foo1(char c) {
    return String.valueOf(c);
  }

  String foo2(Character character) {
    return String.valueOf(character);
  }

  String foo3(char c) {
      /*6*/
      /*7*/
      /*8*/
      /*9*/
      /*10*/
      /*11*/
      return /*1*/String/*2*/./*3*/valueOf/*4*/(/*5*/c/*12*/)/*13*/;
  }

  String foo4(char c) {
    return String.valueOf(((((((c)))))));
  }
}
