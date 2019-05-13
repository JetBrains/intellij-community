class Test {
    void t(java.util.Map<String, String> m) {
        String f = "";
        System.out.println("f = " + newMethod(f) + ", " + m.get(newMethod(f)));
    }

    private String newMethod(String f) {
        return f;
    }
}