/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 23.01.2003
 * Time: 17:59:45
 * To change this template use Options | File Templates.
 */
public class Inner1 {
    private int a = 0;
    private class A extends B{
        {
            int j = <ref>a;
        }
    }
}

class B {
    private int a = 0;
}

