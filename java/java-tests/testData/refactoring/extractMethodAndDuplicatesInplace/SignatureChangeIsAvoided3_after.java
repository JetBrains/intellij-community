public class Test {

    boolean test1() {
        return extracted();
    }

    private boolean extracted() {
        return getSize1() < getLimit1();
    }

    boolean test2() {
      return getSize2() < getLimit2();
    }

    int getSize1() {
      return 42;
    }

    int getSize2() { return 42; }

    int getLimit1() { return 42; }

    int getLimit2() { return 42; }
}