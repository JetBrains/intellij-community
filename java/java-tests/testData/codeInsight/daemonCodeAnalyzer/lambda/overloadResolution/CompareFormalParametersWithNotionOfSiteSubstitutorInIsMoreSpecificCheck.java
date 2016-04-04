import java.util.List;

class TestIntelliJ<T, S extends List<T>> extends SubInterface<T, S> {
  private <H extends SuperInterface<T, S>> H <warning descr="Private method 'test(H)' is never used">test</warning>(H t) {
    System.out.println("SuperInterface" + t);
    return t;
  }

  private <H extends SubInterface<T, S>> H test(H t) {
    System.out.println("SubInterface" + t);
    return t;
  }

  public static void main(String... args) {
    TestIntelliJ<String, List<String>> testIntelliJ = new TestIntelliJ<>();
    testIntelliJ.test(testIntelliJ);
  }
}

interface SuperInterface<T, <warning descr="Type parameter 'S' is never used">S</warning> extends List<T>> {
}

abstract class SubInterface<T, S extends List<T>> implements SuperInterface<T, S> {
}