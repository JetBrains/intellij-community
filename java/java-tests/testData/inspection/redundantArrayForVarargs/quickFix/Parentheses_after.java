public class Main {
    public static void main(String[] args) {
        display(//c1
                "hi"<caret>);
    }

    private static void display(String... messages) { }
}