import java.util.*;
class TestInfer
{
    static <V, T extends V> List<V> singleton(T item)
    {
        ArrayList<V> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    public List<Number> test()
    {
        List<Number> ln = singleton(new Long(1));

        return singleton(new Long(2));
    }
}
