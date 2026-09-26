public class Test {

    void test() {
        if (isBoolean()) {
            System.out.println();
        }
        if (getSize2() < getLimit2()) {
            System.out.println();
        }
    }

    private boolean isBoolean() {
        return getSize1() < getLimit1();
    }

    int getSize1() {
      return 42;
    }

    int getSize2() { return 42; }

    int getLimit1() { return 42; }

    int getLimit2() { return 42; }
}