import java.util.*;
class GenericsTest<T> {

    static <S> S next(GenericsTest<S> test)
    {
        System.out.println(test);
        return null;
    }

    public Iterator<T> iterator()
    {
        return new Iterator<T>() {
            @Override
            public boolean hasNext()
            {
                return false;
            }

            @Override
            public T next()
            {
                return GenericsTest.next(GenericsTest.this);
            }

            @Override
            public void remove()
            {
            }
        };
    }
}

class GenericsTest1<T> {

    static <S> S next1(GenericsTest1<S> test)
    {
        System.out.println(test);
        return null;
    }

    public Iterator<T> iterator()
    {
        return new Iterator<T>() {
            @Override
            public boolean hasNext()
            {
                return false;
            }

            @Override
            public T next()
            {
                return GenericsTest1.next1(GenericsTest1.this);
            }

            @Override
            public void remove()
            {
            }
        };
    }
}


class GenericsTest2<T> {

    static <S> S next2(GenericsTest2<S> test)
    {
        System.out.println(test);
        return null;
    }

    public Iterator<T> iterator()
    {
        return new Iterator<T>() {
            @Override
            public boolean hasNext()
            {
                return false;
            }

            @Override
            public T next()
            {
                return next2(GenericsTest2.this);
            }

            @Override
            public void remove()
            {
            }
        };
    }
}