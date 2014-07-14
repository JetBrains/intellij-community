/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Nov 15, 2004
 * Time: 5:40:02 PM
 * To change this template use File | Settings | File Templates.
 */
class A {}
class B {}

public class Test {
    A getA() {
        return new A ();
    }

    int foo() {
        A a = getA ();

        if (a != null){
            return 0;
        }

        return 1;
    }
}
