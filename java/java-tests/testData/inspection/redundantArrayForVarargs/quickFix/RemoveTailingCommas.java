public class Main {
    public static void main(String[] args) {
        display(new S<caret>tring[]{
                "hi",
        });
    }

    private static void display(String... messages) { }
}