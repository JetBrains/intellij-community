package codeInsight.completion.variables.locals;

public class TestSource3 {
    int aaa = 0;
    public static void foo(){
        int abc = 0, bac = abc<caret>
    }
}
