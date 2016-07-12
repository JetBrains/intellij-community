import static <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>.*;
import <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>;
import <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>.Inner;
import static <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>.Inner.*;
import static <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>.Inner.INNER_CONSTANT;

class MyClient
{
    private int field = MyConstants.CONSTANT;
}

class MyConstants
{
    public static final int CONSTANT = 1;

    public static class Inner {
        public static final String INNER_CONSTANT = "const";
    }
}