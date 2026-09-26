class CommentAfterSelectedFragment {
    foo(boolean debugMode) {
        int i= 0;

        i = newMethod(debugMode, i);
        System.out.println(i);
    }

    private int newMethod(boolean debugMode, int i) {
        if (debugMode) {
            i = 1;
        } /* comment */
        return i;
    }
}