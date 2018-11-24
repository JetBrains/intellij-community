package codeInsight.completion.variables.locals;

public class TestSource4 {
    int aaa = 0;
    public static void foo(){
        final int abc = 0;
        class Inner1{
           int sbe=abc<caret>
        }
    }
}
