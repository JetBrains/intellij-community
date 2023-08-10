
import java.util.ArrayList;

public class NestedMethodCall extends ArrayList{
    private int baz = bar(foo());

    public NestedMethodCall(int initialCapacity) {
        super(Math.abs(initialCapacity));
    }

    public NestedMethodCall() {
        this(Math.abs(3));
    }

    public int foo()
    {
        return 3;
    }
    public int bar(int val)
    {
        return 3+val;
    }

    public int baz()
    {
        bar(Math.abs(3));
        return bar(<warning descr="Nested method call 'foo()'">foo</warning>());
    }

    public int barangus()
    {
        return bar(<warning descr="Nested method call 'foo()'">foo</warning>()+3);
    }

    private int value = 1;
    public int getValue() {
        return value;
    }

    public int apex() {
        return bar(getValue());
    }
}
