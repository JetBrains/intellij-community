class External {
    private int member;

    External() {
        member = 0;
    }

    void testMethod(final int param1, int param2) {
        final int local1 = 0;
        int local2 = 123;
        int local3 = local1;

        {
            int innerLocal1 = 0;
            int innerLocal2 = 7;
            int innerLocal3 = 0;
            int innerLocal4 = 7;
            String incorrect;

            <caret>Runnable runnable = new Runnable() {
                public void run() {
                    int insideRunnable1 = param1 + ((++innerLocal2) << param2);
                    param2 = local1 * insideRunnable1;
                    local3 = param2;
                    int insideRunnable2 = local2;
                    innerLocal1--;
                    --innerLocal3;
                    innerLocal4++;
                    incorrect = "misplaced initialization";
                }
            };
            local2 = local3;
        }
    }
}