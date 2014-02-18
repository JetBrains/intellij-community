import java.util.*;

class Test<T>
{
        List<Collection<T>> getList ()
        {
                return new ArrayList<Collection<T>> ();
        }

        public void test1 (Test<T> arg)
        {
                List<Collection<T>> result = arg.getList ();
                result.add <error descr="'add(java.util.Collection<T>)' in 'java.util.List' cannot be applied to '(java.util.HashMap<java.lang.Integer,java.lang.Integer>)'">(new HashMap<Integer, Integer> ())</error>;
        }

}
