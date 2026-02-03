package p;
import java.io.Closeable;
import java.util.concurrent.*;

<error descr="'java.util.concurrent.Callable' cannot be inherited with different type arguments: 'java.lang.AutoCloseable' and 'java.io.Closeable'">abstract class B extends A implements Callable<Closeable></error> {
}