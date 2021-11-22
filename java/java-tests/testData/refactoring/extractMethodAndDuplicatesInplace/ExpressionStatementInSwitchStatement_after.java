package test;

public class Test1 {
    void test(){
        String s = "sample";
        switch (s) {
            case "one" -> extracted();
            default -> System.out.println();
        };
    }

    private void extracted() {
        foo();
    }

    String foo(){
        return "42";
    }
}
