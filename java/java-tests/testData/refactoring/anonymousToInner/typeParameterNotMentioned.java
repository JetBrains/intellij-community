public class LocalClass {
    <T> void test(T t) {
        Runnable r = new R<caret>unnable() {
            @Override
            public void run() {
                System.out.println(t);
            }
        };
        r.run();
    }
}
