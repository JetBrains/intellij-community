import java.util.List;
class Test2 {

    interface I<X> {
        X foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
 
    {
        bar((List<String> lis<caret>t)->{
            return "sss";
        });
    }
}
