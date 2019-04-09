class C {
    int x;

    int b(boolean[] b, C[] c, int n) {
        int i = n;
        while (i >= 0 && (newMethod(b, i, c, n).expressionResult)) {
            i--;
        }
        return i;
    }

    NewMethodResult newMethod(boolean[] b, int i, C[] c, int n) {
        return new NewMethodResult(b[i] || c[n].x == c[i].x);
    }

    static class NewMethodResult {
        private boolean expressionResult;

        public NewMethodResult(boolean expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    int a(boolean[] b, C[] c, int n) {
        int i = n;
        while (i < c.length && (b[i] || c[n].x == c[i].x)) {
            i++;
        }
        return i;
    }
}