class TryFinally {
    int method() {
        String s = "abcd";

        return newMethod(s);
    }

    private int newMethod(String s) {
        StringBuffer buffer = new StringBuffer();
        try {
            buffer.append(s);
            return buffer.length();
        } finally {
            buffer.clear();
        }
    }
}