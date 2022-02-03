// "Fix all 'Redundant 'String' operation' problems in file" "true"

class Test {
  String foo1(char c) {
    return String.valueOf(c);
  }

  String foo2(Character character) {
    return String.valueOf(character);
  }

  String foo3(char c) {
      /*2*/
      /*3*/
      /*4*/
      /*5*/
      /*6*/
      /*7*/
      /*8*/
      /*9*/
      /*10*/
      /*11*/
      return /*1*/String.valueOf(c)/*12*/;
  }

  String foo4(char c) {
    return String.valueOf((((c))));
  }
}
