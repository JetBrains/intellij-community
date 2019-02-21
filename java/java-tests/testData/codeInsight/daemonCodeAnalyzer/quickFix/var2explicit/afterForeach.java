// "Replace 'var' with explicit type" "true"
final class Example {
    public static void main(String[] args) {
        for (String s : args) {
            System.out.println("s = " + s);
        }
    }
}