package codeInsight.completion.variables.locals;

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 3:10:08 PM
 * To change this template use Options | File Templates.
 */
public class TestSource5 {

    public static class inner{
        static int aaa = 0;
        public void foo(){
            int abc = 0;
        }

        static class Inner1{
            static int sbe = a<caret>
        }
    }
}
