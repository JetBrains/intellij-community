class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 3 -> {
                String x = "x";
            }
            case 1 + 1 -> s = "x";
        }
    }
}