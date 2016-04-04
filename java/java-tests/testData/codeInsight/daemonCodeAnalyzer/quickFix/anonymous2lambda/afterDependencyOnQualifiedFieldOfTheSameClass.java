// "Replace with lambda" "true"

import java.util.function.Function;

public class CtorAndFun {
    public final String value;
    public CtorAndFun(final String value) { this.value = value; }

    public static final Function<CtorAndFun, String> GET_VALUE = input -> input.value;
}