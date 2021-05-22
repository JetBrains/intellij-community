// "Replace with 'orElseThrow'" "true"

import java.util.Optional;

class Main {
  native Optional<String> getOptional();

  void test() {
      /*2*/
      /*3*/
      /*4*/
      /*5*/
      /*6*/
      /*10*/
      /*11*/
      /*7*/
      /*8*/
      /*9*/
      getOptional().orElseThrow(/*1*/IllegalArgumentException::new/*12*/)/*13*/;
  }
}