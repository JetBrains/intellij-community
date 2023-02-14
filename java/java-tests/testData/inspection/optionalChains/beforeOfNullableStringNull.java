// "Replace with 'String.valueOf()'" "true"
import java.util.*;

class Tests {
  String toStr(String str) {
    return /*1*/Optional./*2*/ofNullable<caret>(/*3*/str +/*4*/ "bar")/*5*/.orElse(/*6*/"null");
  }
}
