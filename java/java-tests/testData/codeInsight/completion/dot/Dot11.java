/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.01.2003
 * Time: 19:35:37
 * To change this template use Options | File Templates.
 */
public class Dot11 extends A {
    void foo1(){}

    {
        this.<caret>
    }
}

class A{
    void foo(){}
}