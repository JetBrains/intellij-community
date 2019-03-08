// "Replace 'var' with explicit type" "true"
final class Example {
    public static void main(String[] args) {
        for (v<caret>ar s : args) {
            System.out.println("s = " + s);
        }
    }
}