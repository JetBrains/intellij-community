/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 23.01.2003
 * Time: 18:44:34
 * To change this template use Options | File Templates.
 */
public class Inner1 {
    private int a(){
        return 0;
    }

    private class A extends B{
        {
            int j = <ref>a();
        }
    }
}

class B {
    public int a(){
        return 0;
    }
}
