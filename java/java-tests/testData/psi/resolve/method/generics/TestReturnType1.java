/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.05.2003
 * Time: 16:50:51
 * To change this template use Options | File Templates.
 */
public class TestReturnType1 {
    class A<T extends Runnable>{
        public T foo(T t){
            return null;
        }
    }
    {
        new A<String>().foo(new String()).<ref>toCharArray();
    }
}
