import java.util.concurrent.atomic.AtomicReference;

class Test {
    AtomicReference<Byte> b = new AtomicReference<Byte>((byte) 0);

    void bar() {
        if (b.get() == 0) {
            b.getAndSet(new Byte((byte) (b.get() + 1)));
            b.set(new Byte((byte) (b.get() + 0)));
            //System.out.println(b + 10);
            System.out.println(b.get());
        }
    }
}