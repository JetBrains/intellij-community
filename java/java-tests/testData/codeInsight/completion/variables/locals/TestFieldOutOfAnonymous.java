package codeInsight.completion.variables.locals;

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 3:10:08 PM
 * To change this template use Options | File Templates.
 */
public class TestSource9 {
    private int aField;
    public void foo() {
        Runnable r = new Runnable() {
            public void run() {
               int x = aF<caret>;
            }
        };
    }
}
