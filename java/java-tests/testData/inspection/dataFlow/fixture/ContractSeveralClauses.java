import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public class Foo {

    String foo(Object escaper, String s) {
        return escapeStr(s, escaper);
    }

    String foo2(Object escaper, @Nullable String s) {
        return <warning descr="Expression 'escapeStr(s, escaper)' might evaluate to null but is returned by the method which is not declared as @Nullable">escapeStr(s, escaper)</warning>;
    }

    void foo3(@Nullable String s) {
        foo2(this, escapeStr(s));
    }

    @Contract("null,_->null;!null,_->!null")
    String escapeStr(String s, Object o) {
        return s;
    }

    @Contract("null->null;!null->!null")
    String escapeStr(String s) {
        return s;
    }
}