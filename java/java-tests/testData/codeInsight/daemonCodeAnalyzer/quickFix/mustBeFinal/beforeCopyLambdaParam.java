// "Copy 'o' to final temp variable" "true"
class Test1 {
    void foo(){}
    {
        Comparable<String> a = o->{
            o = "";
            new Runnable() {
                @Override
                public void run() {
                    System.out.println(<caret>o);
                }
            }.run();
            return 0;
        };
    }
}