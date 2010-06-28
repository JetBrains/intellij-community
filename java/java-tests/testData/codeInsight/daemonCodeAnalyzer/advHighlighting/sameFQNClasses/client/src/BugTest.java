import bug.test.BaseClass; 

/**
 * Created by IntelliJ IDEA.
 * Date: Dec 13, 2007
 * Time: 4:15:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class BugTest {
    public static void main(){
        BaseClass testClass = new BaseClass();

        testClass.doSomething(5);

        testClass.doSomethingElse(5);
    }
}
