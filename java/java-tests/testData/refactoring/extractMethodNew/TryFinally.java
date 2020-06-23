class TryFinally {
    int method() {
        String s = "abcd";
     
        <selection>StringBuffer buffer = new StringBuffer();
        try {
            buffer.append(s);
            return buffer.length();
        } finally {
            buffer.clear();
        }</selection>
    }
}