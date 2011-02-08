
/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.05.2003
 * Time: 16:24:03
 * To change this template use Options | File Templates.
 */
public class TestOverloading1 {
    class A{
        void foo(){}
    }
    class B extends A{
        void foo(){}
    }

    {
        new B().<ref>foo(1);
    }
}
