import org.jetbrains.annotations.*;

import java.util.*;

class Test {
    @Nullable
    public String getString(String s) {
        return null;
    }

    void test(Collection<String> foos) {
        Test t = new Test();

        final int i = t.getString("foo").<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>();
        System.out.println("i = " + i);

        for (String foo : foos) {
            final int j = t.getString(foo).<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>();
            System.out.println("i = " + j);
        }
    }

    void test2(Collection<String> foos) {
        Test t = new Test();

        for (Iterator<String> iterator = foos.iterator(); iterator.hasNext();) {
            final int i = getString(iterator.next()).<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>();
            System.out.println("i = " + i);
        }
    }
}