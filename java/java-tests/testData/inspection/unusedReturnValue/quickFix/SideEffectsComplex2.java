import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private int in<caret>cOrDec(boolean b, AtomicInteger x) {
        if (b) return x.incrementAndGet()+1;
        System.out.println("dec");
        return x.decrementAndGet()+1;
    }

    public void test() {
        incOrDec(true, new AtomicInteger());
    }
}