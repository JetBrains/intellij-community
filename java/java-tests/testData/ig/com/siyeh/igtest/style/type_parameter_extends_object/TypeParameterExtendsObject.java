import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

public class TypeParameterExtendsObject<<warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">E</warning> extends @TypeParameterExtendsObject.A Object> {
    @Target(ElementType.TYPE_USE)
    public @interface A{}

    public static final class Inner1<<warning descr="Type parameter 'T' explicitly extends 'java.lang.Object'">T</warning> extends Object> {}
    public static final class Inner2<<warning descr="Type parameter 'T' explicitly extends 'java.lang.Object'">T</warning> extends @A Object> {}
    public static final class Inner3<T extends List<Integer>> {}

    public <<warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">E</warning> extends Object>void foo(E e) {}
    public <<warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">E</warning> extends @A Object>void foo2(E e) {}
    public <E extends List>void foo3(E e) {}

    List<<warning descr="Wildcard type argument '?' explicitly extends 'java.lang.Object'">?</warning> extends @A Object> list;
    List<<warning descr="Wildcard type argument '?' explicitly extends 'java.lang.Object'">?</warning> extends Object> list2;
    List<? extends List> <error descr="Variable 'list2' is already defined in the scope">list2</error>;
}
