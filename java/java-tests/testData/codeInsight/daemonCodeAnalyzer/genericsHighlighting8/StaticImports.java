
import static java.util.Arrays.asList;
import static java.util.Arrays.sort;
<warning descr="Unused import statement">import static java.util.Arrays.binarySearch;</warning>

public class StaticImports {
    {
        asList(new Object[]{});
    }

    void method() {
        sort(new long[0]);
//        sort< error descr="Cannot resolve method 'sort()'">()< /error>;
    }

}
