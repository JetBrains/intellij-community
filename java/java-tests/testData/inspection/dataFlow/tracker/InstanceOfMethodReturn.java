/*
Value is always true (x instanceof String; line#15)
  'x' was assigned (=; line#13)
    Method return type is String (str(); line#13)
 */

import java.util.Objects;

class Test {
  String str() {return "foo";}
  
  void test() {
    Object x = str();
    System.out.println(x.hashCode());
    if(<selection>x instanceof String</selection>) {
      
    }
  }
}