import org.jetbrains.annotations.NotNull;

class Test1 {
    void foo(){}
    {
        String str = "effectively final string";
        String str1 = "effectively final string";
        Comparable<String> a = new Comparable<String>() {
            @Override
            public int compareTo(@NotNull String o) {
                System.out.println(str1);
                new Runnable() {
                    @Override
                    public void run() {
                        System.out.println(o);
                        System.out.println(str);
                    }
                }.run();
                return 0;
            }
        };
    }
}