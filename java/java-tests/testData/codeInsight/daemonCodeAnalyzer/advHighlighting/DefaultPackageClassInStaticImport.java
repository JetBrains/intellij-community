import static <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>.*;
import <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>;

class MyClient
{
    private int field = MyConstants.CONSTANT;
}

class MyConstants
{
    public static final int CONSTANT = 1;
}