public class Class1 {
    interface Lamb {
        Object call(Object... args);
    }

    public void m(Lamb l) {

    }

    public static void main(String[] args) {
        new Ex().m((arg) -> {
            return "<caret>"
        });
    }
}