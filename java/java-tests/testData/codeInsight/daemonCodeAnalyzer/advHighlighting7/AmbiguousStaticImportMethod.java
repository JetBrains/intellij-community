package pck;
import static pck.A.*;
import static pck.B.*;

class Test {
    public static void main(String[] args) {
        bar<error descr="Ambiguous method call: both 'A.bar(Object)' and 'B.bar(Object)' match">("")</error>;
    }
}