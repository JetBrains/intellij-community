import java.util.List;

final class Test {
    @SafeVarargs
    private <T> void testPrivate(T... <warning descr="Parameter 'i' is never used">i</warning>){}

    <error descr="@SafeVarargs is not allowed on non-final instance methods">@SafeVarargs</error>
    protected <T> void testProtected(T... <warning descr="Parameter 'i' is never used">i</warning>){} //but in final class

    public static void main(String[] args) {
        new Test().testPrivate();
        new Test().testProtected();
    }
}
