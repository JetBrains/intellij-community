/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 22.01.2003
 * Time: 11:15:19
 * To change this template use Options | File Templates.
 */
public class Test1 {
    public static class A{
        protected void foo(){}
    }

    public static void main(String[] args){
        new A(){
            protected void foo(){
                super.<ref>foo();
            }
        };
    }
}
