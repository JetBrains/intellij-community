import java.util.List;

class Test {

    interface P {

    }

    public abstract class AP implements P {

    }

    public class AP1 extends AP {

    }

    public class AP2 extends AP {

    }

    private static final List<Class<AP1>> AP_LIST = listOf(AP1.class);


    private static <T> List<T> listOf(T... ts) {
        return null;
    }

    public static void test() {
        List<Class<? extends AP>> apList1 = <error descr="Inconvertible types; cannot cast 'java.util.List<java.lang.Class<Test.AP1>>' to 'java.util.List<java.lang.Class<? extends Test.AP>>'">(List<Class<? extends AP>>) AP_LIST</error>;
        List<Class<? super AP1>> apList2 = <error descr="Inconvertible types; cannot cast 'java.util.List<java.lang.Class<Test.AP1>>' to 'java.util.List<java.lang.Class<? super Test.AP1>>'">(List<Class<? super AP1>>) AP_LIST</error>;
    }
}
