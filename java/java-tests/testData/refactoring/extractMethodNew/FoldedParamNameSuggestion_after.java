class Test {
    String get(String[] args) {
        return newMethod(args[0]);
    }

    private String newMethod(String arg1) {
        String arg = arg1;
        return arg;
    }
}