
import java.io.IOException;

@FunctionalInterface
interface ThrowableRunnable<E extends Exception> {
    
    void get() throws E;

    static <E extends Exception> void m(ThrowableRunnable<E> supplier, Object... params) throws E {}


    default void getContent() {
        try {
            m(() -> { throw new IOException(); });
        } catch (IOException e) { }
    }

}