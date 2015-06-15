import java.util.List;

<error descr="'@SafeVarargs' not applicable to type">@SafeVarargs</error>
class SafeVarargsTests {
    //fixed arity
    <error descr="@SafeVarargs is not allowed on methods with fixed arity">@SafeVarargs</error>
    public void testNonVarargs1(){}

    <error descr="@SafeVarargs is not allowed on methods with fixed arity">@SafeVarargs</error>
    public void testNonVarargs2(int <warning descr="Parameter 'i' is never used">i</warning>){}

    <error descr="@SafeVarargs is not allowed on methods with fixed arity">@SafeVarargs</error>
    public <T> void testNonVarargs3(T <warning descr="Parameter 't' is never used">t</warning>){}

    //non static/non final
    <error descr="@SafeVarargs is not allowed on non-final instance methods">@SafeVarargs</error>
    public void testNonVarargs4(int... <warning descr="Parameter 'i' is never used">i</warning>){}

    //reassigned
    @SafeVarargs
    public final <T> void testT(T[] tt, T... t) {
        <warning descr="@SafeVarargs do not suppress potentially unsafe operations">t</warning> = tt;
        System.out.println(t[0]);
    }

    //incorrect types
    @SafeVarargs
    public final void testString(<warning descr="@SafeVarargs is not applicable for reifiable types">String...</warning> <warning descr="Parameter 'str' is never used">str</warning>){
    }

    @SafeVarargs
    public final void testStringArray(<warning descr="@SafeVarargs is not applicable for reifiable types">String[]...</warning> <warning descr="Parameter 'str' is never used">str</warning>){
    }

    @SafeVarargs
    public static void testUnbound(<warning descr="@SafeVarargs is not applicable for reifiable types">List<?>...</warning> <warning descr="Parameter 't' is never used">t</warning>){}


    //correct usages
    @SafeVarargs
    public static <T> void foo(T... <warning descr="Parameter 't' is never used">t</warning>){}
    @SafeVarargs
    public static <T> void foo1(List<T>... <warning descr="Parameter 't' is never used">t</warning>){}
    @SafeVarargs
    public static <T> void foo2(List<? extends T>... <warning descr="Parameter 't' is never used">t</warning>){}
    @SafeVarargs
    public static <T> void foo2(java.util.Map<?, T>... <warning descr="Parameter 't' is never used">t</warning>){}

}

abstract class AClass {
    @SafeVarargs
    <T> AClass(T... d){
      System.out.println(d);
    }
}

class ABClass extends AClass {
    @SafeVarargs
    <T> ABClass(T... d){
        super(d);
    }
}