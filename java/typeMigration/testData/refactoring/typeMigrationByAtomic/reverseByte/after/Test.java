import java.util.concurrent.atomic.AtomicReference;

class Test {
    byte b = (byte) 0;

    void bar() {
        if (b == 0) {
            b = new Byte((byte) (b + 1));
            b = new Byte((byte) (b + 0));
            //System.out.println(b + 10);
            System.out.println(b);
        }
    }
}