import java.util.*;

class C {
    {
        Object[] o = null;
        List l = <selection>new ArrayList(Arrays.asList(o))</selection>;

        List l1 = new ArrayList(Arrays.asList(new Object[0]));
    }
}