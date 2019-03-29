class CommentAfterSelectedFragment {
    void foo(boolean debugMode) {
        int i= 0;

        NewMethodResult x = newMethod(debugMode);
        if (debugMode) {
            i = 1;
        } /* comment */
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

    class NewMethodResult {
        private int i;

        public NewMethodResult(int i) {
            this.i = i;
        }
    }
}