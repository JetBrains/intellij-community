package test;

public class Test1 {
    void test(){
        String s = "sample";
        switch (s) {
            case "one" -> <selection>foo();</selection>
            default -> System.out.println();
        };
    }

    String foo(){
        return "42";
    }
}
