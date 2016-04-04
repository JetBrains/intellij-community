import java.util.List;
class GetClassTest {
    interface GetCl {
        Class<? extends List> _();
    }

    interface GetClReturnTypeProblems {
        Class<List<String>> _();
    }

    void test(int[] iarr, List<String> ls) {
        GetCl c4 = ls::getClass;
        GetClReturnTypeProblems c5 = <error descr="Bad return type in method reference: cannot convert java.lang.Class<? extends java.util.List<java.lang.String>> to java.lang.Class<java.util.List<java.lang.String>>">ls::getClass</error>;
    }
}
