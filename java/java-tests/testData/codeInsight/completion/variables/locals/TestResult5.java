package codeInsight.completion.variables.locals;

public class TestSource5 {

    public static class inner{
        static int aaa = 0;
        public void foo(){
            int abc = 0;
        }

        static class Inner1{
            static int sbe = aaa<caret>
        }
    }
}
