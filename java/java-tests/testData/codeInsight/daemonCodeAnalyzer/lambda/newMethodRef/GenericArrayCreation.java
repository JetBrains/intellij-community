import java.util.List;

class Test {

    interface I {
        Test m(List<Integer> l1, List<Integer> l2);
    }

    static Test meth(List<Integer>... lli) {
        return null;
    }

    Test(List<Integer>... lli) {}

    {
        I <warning descr="Variable 'i1' is never used">i1</warning> = <warning descr="Unchecked generics array creation for varargs parameter">Test::meth</warning>;
        I <warning descr="Variable 'i2' is never used">i2</warning> = <warning descr="Unchecked generics array creation for varargs parameter">Test::new</warning>;
    }
}
