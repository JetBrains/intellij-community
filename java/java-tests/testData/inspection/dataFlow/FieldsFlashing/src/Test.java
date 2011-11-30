import org.jetbrains.annotations.*;

public class TestNullableIntervening   // See http://www.jetbrains.net/jira/browse/IDEA-2845
{
    @Nullable Object obj;

    public TestNullableIntervening(final @Nullable Object obj) {
        this.obj = obj;
    }

    @Nullable Object foo() { return null; }
    void notnull(@NotNull Object arg) {}

    void test1() {
        if (obj != null) {
            // Method intervening, might change obj; should have warning (OK)
            obj = foo();
            notnull(obj);
        }
    }

    void test2() {
        if (obj != null) {
            // Simple assignment intervening, no method calls and nothing
            // involving obj...
            // Should be no warning, but there is
            int x = 10;
            notnull(obj);
        }
    }

    void test3() {
        if (obj != null) {
            // Constructor call intervening; for all we know, this could
            // change obj through some strange interaction (constructor
            // calls static method which has access to this object which
            // then changes obj). Should be a warning (OK).
            TestNullableIntervening obj2 = new TestNullableIntervening(null);
            notnull(obj);
        }
    }

    void test4() {
        if (obj != null) {
            // Array construction cannot change other objects
            // Should be no warning, but there is
            Object[] arr = new Object[5];
            notnull(obj);
        }
    }
}
