public class A {
    public static void main(String[] args) {
        String x = "";
        String s = switch(x) {
            case "name" -> lo<caret>
            default -> "String";
        };
    }
}