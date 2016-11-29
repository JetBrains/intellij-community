public class Bar {
    {
        String foo1 = "";
        String foo2 = "";
        synchronized (foo1) {
            <caret>
        }
    }
}