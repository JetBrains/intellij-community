class CommentAfterSelectedFragment {
    foo(boolean debugMode) {
        int i= 0;
        <selection>
        if (debugMode) {
            i = 1;
        } /* </selection>comment */
        System.out.println(i);
    }
}