import java.util.*;
class Test {
    {
        String[] a = new String[] {
                // workaround for QC CR #80581
                "actioninvocationdata"

        };
        final List<String> foo = Arrays.asList(<caret>a);
    }
}