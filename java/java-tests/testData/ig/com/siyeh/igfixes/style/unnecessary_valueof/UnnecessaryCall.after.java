class UnnecessaryCall {
    void sb(StringBuilder sb, int a, int b) {
        sb.append(a + b);
        sb.append("Number: " + (a + b));
        sb.append(++a);
        sb.append("Number: " + (a << b));
        sb.append(a - b + "Number");
        sb.append((a << b) + "Number");
    }
}