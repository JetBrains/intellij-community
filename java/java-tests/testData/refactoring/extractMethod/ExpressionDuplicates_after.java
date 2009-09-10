import java.util.*;

class C {
    {
        Object[] o = null;
        List l = newMethod(o);

        List l1 = newMethod(new Object[0]);
    }

    private ArrayList newMethod(Object[] o) {
        return new ArrayList(Arrays.asList(o));
    }
}