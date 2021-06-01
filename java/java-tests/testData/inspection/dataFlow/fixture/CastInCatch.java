public class CastInCatch {
  void use() {
    try {
      test();
    }
    catch (Exception e) {
      Ex ex = (<warning descr="Casting 'e' to 'Ex' may produce 'ClassCastException'">Ex</warning>) e;
    }
  }

  void test() throws Ex {

  }
}
class Ex extends Exception {}