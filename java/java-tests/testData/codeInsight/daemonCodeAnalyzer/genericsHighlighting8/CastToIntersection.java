import java.io.Serializable;

public class FooBar1 {
    {
        Object x = null;
        Object y = (CharSequence & Serializable) x;
        Object y2 = (CharSequence & <error descr="Interface expected here">Integer</error>) x;
        Object y3 = (Integer & CharSequence) x;
        <error descr="Incompatible types. Found: 'java.lang.CharSequence & java.io.Serializable', required: 'int'">int y1 = (CharSequence & Serializable) x;</error>
    }
}
