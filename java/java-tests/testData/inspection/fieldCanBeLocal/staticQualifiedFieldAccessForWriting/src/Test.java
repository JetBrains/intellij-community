class G{
    private static boolean foo = true;
    static void bar(){
        if(foo)
            G.foo = false;
    }
}