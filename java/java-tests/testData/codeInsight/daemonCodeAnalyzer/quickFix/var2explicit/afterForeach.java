// "Replace 'var' with explicit type" "true-preview"
final class Example {
    public static void main(String[] args) {
        for (String s : args) {
            System.out.println("s = " + s);
        }
    }
}