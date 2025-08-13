public enum InlineVararg {
    A, B;
    
    private static void toInline(InlineVararg... args<caret>) {
        System.out.println(Arrays.asList(args));
    }
    
    public static void call() {
        toInline(InlineVararg.values());
    }
}