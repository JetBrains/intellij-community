import java.util.function.Supplier;

class MyTest {
    public static void main(Supplier<? extends Exception> supplierException) {
        <error descr="Unhandled exception: java.lang.Exception">throw supplierException.get();</error>
    }
}
