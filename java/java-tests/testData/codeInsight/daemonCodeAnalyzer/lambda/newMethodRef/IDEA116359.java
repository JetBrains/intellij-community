class Tmp
{
    interface Callable<V> {
        V call() throws Exception;
    }
    public static void main(String[] args)
    {
        submit(()->"");
        submit(()->{});
        submit(Tmp::m1);
        submit(Tmp::m2);
        submit(()->m1());
        submit(()->m2());
    }

    static void submit(Callable<String> task)
    {
        System.out.println("Callable");
    }

    static void submit(Runnable task)
    {
        System.out.println("Runnable");
    }

    static String m1(){ return ""; }
    static void m2(){ }
}