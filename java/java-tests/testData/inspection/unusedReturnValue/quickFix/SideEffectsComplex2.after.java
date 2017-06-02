import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private void incOrDec(boolean b, AtomicInteger x) {
        if (b) {
            x.incrementAndGet();
            return;
        }
        System.out.println("dec");
        x.decrementAndGet();
    }

    public void test() {
        incOrDec(true, new AtomicInteger());
    }
}