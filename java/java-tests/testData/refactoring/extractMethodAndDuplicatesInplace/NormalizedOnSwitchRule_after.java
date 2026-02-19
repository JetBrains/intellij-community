package test;

public class Test1 {
    void test(){
        String s = "sample";
        switch (s) {
            case "one" -> extracted();
            default -> System.out.println();
        }
    }

    private static void extracted() {
        System.out.println("one");
        System.out.println("two");
    }
}
