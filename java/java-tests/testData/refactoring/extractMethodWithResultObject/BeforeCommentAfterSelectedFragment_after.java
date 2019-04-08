class CommentAfterSelectedFragment {
    void foo(boolean debugMode) {
        int i= 0;

        NewMethodResult x = newMethod(debugMode);
        System.out.println(i);
    }//ins and outs
//in: PsiParameter:debugMode
//out: PsiLocalVariable:i
//exit: SEQUENTIAL PsiIfStatement

    NewMethodResult newMethod(boolean debugMode) {
        int i = 0;
        if (debugMode) {
            i = 1;
        } /* comment */
        return new NewMethodResult(i);
    }

    static class NewMethodResult {
        private int i;

        public NewMethodResult(int i) {
            this.i = i;
        }
    }
}