import java.util.*;

@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface AssertTrue {}

class Use
{
    interface MyList123{}

    final List<?> list = new test.@AssertTrue MyList123<>();
}
