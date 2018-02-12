// "Convert to ThreadLocal" "true"
import java.util.Arrays;

class Foo {
    private final ThreadLocal<char[]> lookahead = ThreadLocal.withInitial(() -> new char[0]);

  { lookahead.set(Arrays.copyOf(lookahead.get(), 42));}
}