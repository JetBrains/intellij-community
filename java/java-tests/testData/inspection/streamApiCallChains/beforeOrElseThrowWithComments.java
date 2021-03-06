// "Replace with 'orElseThrow'" "true"

import java.util.Optional;

class Main {
  native Optional<String> getOptional();

  void test() {
    getOptional().orElseGet<caret>(/*1*/(/*2*/)/*3*/ ->/*4*/ {/*5*/
      throw/*6*/ new/*7*/ IllegalArgumentException/*8*/(/*9*/)/*10*/;/*11*/
    }/*12*/)/*13*/;
  }
}