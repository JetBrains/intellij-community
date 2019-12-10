import java.util.function.Function;

abstract class Logger {
    public void error(Throwable t) { }
    public abstract void error(String message, Throwable t, String... details);
}

public class JavaTest {
    private static Logger ourLogger = null;

    void test() {
        test2(<error descr="Bad return type in method reference: cannot convert void to java.lang.Void">ourLogger::error</error>);
        test3(<error descr="Bad return type in method reference: cannot convert void to java.lang.Integer">ourLogger::error</error>);
    }

    void test2(Function<Throwable, Void> x) { }
    void test3(Function<Throwable, Integer> x) { }
}