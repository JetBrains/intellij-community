/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.05.2003
 * Time: 16:50:51
 * To change this template use Options | File Templates.
 */
public class TestGenericMethodOverloading3 {
    class A<T>{
        public boolean equals(T t){
            return false;
        }
    }
    {
        new A<String>().<ref>equals(new Object());
    }
}
