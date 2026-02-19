package test;

public class Test1 {
    void test(){
        String s = "sample";
        String result = switch (s) {
            case "one" -> <selection>foo();</selection>
            default -> "default";
        };
    }

    String foo(){
        return "42";
    }
}
