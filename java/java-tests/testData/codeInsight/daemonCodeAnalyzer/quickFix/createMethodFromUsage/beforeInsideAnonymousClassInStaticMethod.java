// "Create method 'doSomething' in 'Test'" "true"
public class Test {
    public static void main(String[] args) {
        blaat(new Runnable() {
            public void run() {
                <caret>doSomething();
            }
        });
    }

    public static void blaat(Runnable o) {}
}
