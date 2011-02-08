/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 23.01.2003
 * Time: 17:59:45
 * To change this template use Options | File Templates.
 */
public class FieldsAndLocals {
    private int a = 0;
    {
        int a = -1;
        new B(){
            public void run(){
                int b = <ref>a
            }
        }
    }
}

abstract class B implements Runnable {
    private int a = 0;
}

