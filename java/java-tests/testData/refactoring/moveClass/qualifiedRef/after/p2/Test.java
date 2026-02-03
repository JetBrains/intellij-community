package p2;
import p1.StaticMethod;

import java.math.BigDecimal;
public class Test extends BigDecimal {
    Test() {super(0);}
    void test() {
        valueOf(0);
        valueOf(StaticMethod.bar());
        valueOf(StaticMethod.bar());
    }
}