// "Copy 'o' to final temp variable" "true"
class Test1 {
    void foo(){}
    {
        Comparable<String> a = o->{
            o = "";
            final String finalO = o;
            new Runnable() {
                @Override
                public void run() {
                    System.out.println(finalO);
                }
            }.run();
            return 0;
        };
    }
}