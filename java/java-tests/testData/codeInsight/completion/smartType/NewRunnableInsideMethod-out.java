public class Test {

    public static void run(Runnable runnable) {
    }

    public static void main(String[] args) {
        run(new Runnable() {
            @Override
            public void run() {
                <caret>
            }
        });
    }
}
