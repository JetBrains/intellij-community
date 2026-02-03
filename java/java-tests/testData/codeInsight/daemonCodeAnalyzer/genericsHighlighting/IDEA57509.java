import java.util.List;

abstract class X {
    abstract <T> void copy(List<T> dest, List<? extends T> src);

    void foo(List<? super Throwable> x, List<? extends Exception> y){
        copy(x, y);
    }
}