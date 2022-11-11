public class Test {

    boolean test1() {
        extracted();
    }

    private static void extracted() {
        System.out.println("one");
        System.out.println("two");
    }

    boolean test2() {
        extracted();
    }

    boolean test3() {
      System.out.println("one");
      System.out.println("parametrized");
    }
}