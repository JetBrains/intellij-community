package test;

public class Test1 {
    void test(){
        String s = "sample";
        String result = switch (s) {
            case "one" -> getFoo();
            default -> "default";
        };
    }

    private String getFoo() {
        return foo();
    }

    String foo(){
        return "42";
    }
}
