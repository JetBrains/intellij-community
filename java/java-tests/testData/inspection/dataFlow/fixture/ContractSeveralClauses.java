import org.jetbrains.annotations.Contract;

public class Foo {

    String foo(Object escaper, String s) {
        return escapeStr(s, escaper);
    }

    @Contract("null,_->null;!null,_->!null")
    String escapeStr(String s, Object o) {
        return s;
    }
}