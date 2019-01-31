import java.util.List;

class Test {
  void test(boolean x, boolean y) {
    if(!x && y) {

    } else if(!x && <warning descr="Condition '!y' is always 'true' when reached">!y</warning>) {

    }
  }

  void test(String type) {
    if(type != null && (type.equals("foo") | type.equals("bar"))) {
      System.out.println("Who knows");
    }
  }
}