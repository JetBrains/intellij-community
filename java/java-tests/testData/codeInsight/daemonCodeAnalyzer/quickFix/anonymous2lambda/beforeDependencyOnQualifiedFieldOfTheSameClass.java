// "Replace with lambda" "true-preview"

import java.util.function.Function;

public class CtorAndFun {
    public final String value;
    public CtorAndFun(final String value) { this.value = value; }

    public static final Function<CtorAndFun, String> GET_VALUE = new Funct<caret>ion<CtorAndFun, String>() {
      @Override public String apply(final CtorAndFun input) {
        return  input.value;
      }
    };
}