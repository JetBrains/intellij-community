// "Replace 'var' with explicit type" "true-preview"
final class Example {
    public static void main(String[] args) {
        for (v<caret>ar s : args) {
            System.out.println("s = " + s);
        }
    }
}