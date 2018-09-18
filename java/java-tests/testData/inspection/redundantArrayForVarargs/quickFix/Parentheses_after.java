public class Main {
    public static void main(String[] args) {
        display(//c1
                <caret>"hi");
    }

    private static void display(String... messages) { }
}