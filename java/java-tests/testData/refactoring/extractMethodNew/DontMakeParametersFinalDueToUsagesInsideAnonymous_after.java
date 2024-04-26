import org.jetbrains.annotations.NotNull;

class Box {
    private void test(String str1, String str2) {
        Data data = newMethod(str1, str2);
        System.out.println(data);
    }

    private @NotNull Data newMethod(String str1, String str2) {
        return new Data() {
            @Override
            public String getA() {
                return str1;
            }

            @Override
            public String getB() {
                return str2;
            }
        };
    }

    static interface Data {
        String getA();
        String getB();
    }
}