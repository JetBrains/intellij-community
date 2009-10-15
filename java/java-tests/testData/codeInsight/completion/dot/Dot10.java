/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 23.01.2003
 * Time: 17:06:57
 * To change this template use Options | File Templates.
 */
public class Dot9 {
    public static class A{
        public A(){}
        public void foo(){}
    }

    public static void main(String[] args) {
        long[] arr = new long[0];
        arr.<caret>
    }
}
