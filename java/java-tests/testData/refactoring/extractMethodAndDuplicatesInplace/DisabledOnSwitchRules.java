package test;

public class Test1 {
    void test(){
        String s = "sample";
        switch (s) {
            <selection>case "one" -> {
                System.out.println("one");
                System.out.println("two");
            }
            default -> System.out.println();</selection>
        }
    }
}
