// "Replace with collect" "true"

import java.util.List;

class A {
  B next() {return null;}
  int x;
}

class B extends A {}


public class Main {
  public static int find(List<List<String>> list) {
    StringBuilder sb = new StringBuilder();
    for <caret> (A a = new A();; a = a.next()) {
      if(a.x % 100 == 0) {
        sb.append(a.x);
      }
    }
  }
}