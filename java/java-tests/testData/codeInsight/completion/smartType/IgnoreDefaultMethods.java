interface Predicate<T> {
   public boolean test(T t);
    public default Predicate<T> and(Predicate<? super T> p) {
        return null;
    }
}


public class Test1 {
    {
        Predicate p = new Predic<caret>
    }
}
