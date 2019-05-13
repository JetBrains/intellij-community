package my;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class Java8Private {
  public static void main(String[] args) {
    new Java8Private().foo();
  }

  private void foo() {
    final Map<Integer,Integer> map = new HashMap<Integer,Integer>();
    map.put(1, 2);
    //evaluate here  map.entrySet().stream().filter((a) -> (a.getKey()>0));
    <caret>new Inner(map).invoke();
    map.put(3, 5);
  }

  private void zoo(int a) {
    System.out.println("DONE " + a);
  }


  public class Inner extends MagicAccessorBridge {
    final Map<Integer,Integer> map;

    public Inner(Map<Integer, Integer> map) {
      this.map = map;
    }

    void invoke() {
      map.entrySet().stream().forEach((a) -> accessorZoo(Java8Private.this, a.getValue()));
    }

    // accessor
    void accessorZoo(Java8Private obj, int a) {
      obj.zoo(a);
    }
  }
}

class MagicAccessorBridge {
}