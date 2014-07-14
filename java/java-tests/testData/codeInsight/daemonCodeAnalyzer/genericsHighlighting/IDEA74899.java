import java.util.Collections;
import java.util.Map;

class IDEA74899 {
    void foo() {
        Map<String, String> m = Collections.emptyMap();
        if (<error descr="Operator '==' cannot be applied to 'java.util.Map<java.lang.String,java.lang.String>', 'java.util.Map<java.lang.Object,java.lang.Object>'">m == Collections.emptyMap()</error>) {
           return;
        }
    }
}
