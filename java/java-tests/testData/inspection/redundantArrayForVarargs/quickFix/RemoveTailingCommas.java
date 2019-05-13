public class Main {
    public static void main(String[] args) {
        display(new //c1
                  S<caret>tring[]{
                "hi",
        });
    }

    private static void display(String... messages) { }
}