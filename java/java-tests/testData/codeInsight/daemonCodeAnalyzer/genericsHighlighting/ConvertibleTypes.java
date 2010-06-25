import java.util.Collection;
import java.util.ArrayList;

interface YO<<warning descr="Type parameter 'T' is never used">T</warning>> {}
interface YO1 extends YO<String> {}
interface YO2 extends YO<Integer> {}

public class ConvertibleTest {

    YO2 bar (YO1 s) {
          return <error descr="Inconvertible types; cannot cast 'YO1' to 'YO2'">(YO2) s</error>;
    }
}


//IDEA-1097
interface Interface1 {}
interface Interface2 {}

class Implementation implements Interface1 {}

public class InconvertibleTypesTest <E extends Interface2>
{
    E thing;

    public E getThing() {
         return thing;
    }

    Implementation foo(InconvertibleTypesTest<? extends Interface1> i2) {
       //This is a valid cast from intersection type
       return (Implementation) i2.getThing();
    }
}

class MyCollection extends ArrayList<Integer> {}
class Tester {
    Collection<String> x(MyCollection l) {
        return <error descr="Inconvertible types; cannot cast 'MyCollection' to 'java.util.Collection<java.lang.String>'">(Collection<String>) l</error>;
    }
}


class IDEADEV3978 {
    class Constructor<T> {
        Class<T> getDeclaringClass() {
            return null;
        }
    }
    public static void foo(Constructor<?> constructor) {
        if(constructor.getDeclaringClass() == String.class) { //captured wildcard is convertible
            System.out.println("yep");
        }
    }
}

class C2<T> {
   void f(T t) {
     if (t instanceof Object[]) return;
   }
}

class Casting {
 void f(Object o)
 {
         if (o instanceof int[]) return;

         Object obj1 = (Object)true;   f(obj1);
         Object ob = (Number)1;        f(ob);
         Object ob2 = (Object)1;       f(ob2);

 }

 public static <T> T convert(Class<T> clazz, Object obj) {
     if (obj == null) return null;
     if (String[].class == clazz)
        return <warning descr="Unchecked cast: 'java.lang.String[]' to 'T'">(T) parseArray(obj)</warning>;
     return null;
 }

 private static String[] parseArray(Object obj) {
    return obj.toString().split(",");
 }
}