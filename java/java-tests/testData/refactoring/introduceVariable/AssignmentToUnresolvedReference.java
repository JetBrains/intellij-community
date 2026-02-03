import java.util.*;

public class Test<T>
{
        List<Collection<T>> getList ()
        {
                return new ArrayList<Collection<T>> ();
        }

        public void test2 (Test<?> arg)
        {
                res<caret>ult = arg.getList ();
        }
}