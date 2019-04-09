class CommentAfterSelectedFragment {
    void foo(boolean debugMode) {
        int i= 0;

        NewMethodResult x = newMethod(i, debugMode);
        i = x.i;
        System.out.println(i);
    }

    NewMethodResult newMethod(int i, boolean debugMode) {
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