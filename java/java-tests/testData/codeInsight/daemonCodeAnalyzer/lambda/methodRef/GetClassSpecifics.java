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
        <error descr="Incompatible types. Found: '<method reference>', required: 'GetClassTest.GetClReturnTypeProblems'">GetClReturnTypeProblems c5 = ls::getClass;</error>
    }
}
