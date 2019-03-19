class CommentAfterSelectedFragment {
    foo(boolean debugMode) {
        int i= 0;

        if (debugMode) {
            i = 1;
        } /* comment */
        System.out.println(i);
    }//ins and outs
//in: PsiParameter:debugMode
//out: PsiLocalVariable:i
}