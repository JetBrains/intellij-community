import org.intellij.lang.annotations.MagicConstant;

class MagicString {
    interface Options {
        String FOO = "foo";
        String BAR = "bar";
        String BAZ = "baz";
    }

    void test2(@MagicConstant(valuesFromClass = Options.class) String value) {
        switch<caret> (value) {
            case Options.FOO:
                break;
            case "bar":break;
            case Options.BAZ:
                break;
        }
    }
}