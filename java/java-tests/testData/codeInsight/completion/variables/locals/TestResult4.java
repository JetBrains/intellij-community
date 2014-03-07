package codeInsight.completion.variables.locals;

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 3:10:08 PM
 * To change this template use Options | File Templates.
 */
public class TestSource4 {
    int aaa = 0;
    public static void foo(){
        final int abc = 0;
        class Inner1{
           int sbe=abc<caret>
        }
    }
}
