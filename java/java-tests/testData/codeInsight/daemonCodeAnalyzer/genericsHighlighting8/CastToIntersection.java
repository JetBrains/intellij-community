import java.io.Serializable;
import java.util.*;

class FooBar1 {
    {
        Object x = null;
        Object y = (CharSequence & Serializable) x;
        Object y2 = (CharSequence & <error descr="Interface expected here">Integer</error>) x;
        Object y3 = (Integer & CharSequence) x;
        <error descr="Incompatible types. Found: 'java.lang.CharSequence & java.io.Serializable', required: 'int'">int y1 = (CharSequence & Serializable) x;</error>
        Object z0 = (Cloneable & <error descr="Unexpected type: class is expected">Runnable[]</error>) x;
        Object z1 = (Cloneable & <error descr="Repeated interface">Cloneable</error>)  x;
        Object z2 = <error descr="java.util.Collection cannot be inherited with different arguments: String and Integer">(List<String> & Set<Integer>) x</error>;
    }
}
