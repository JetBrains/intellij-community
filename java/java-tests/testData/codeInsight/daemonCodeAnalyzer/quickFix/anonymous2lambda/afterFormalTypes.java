// "Replace with lambda" "true"
class Test2 {

    interface I<X> {
        X foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
 
    {
        bar((List<String> list)->{
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        });
    }
}
