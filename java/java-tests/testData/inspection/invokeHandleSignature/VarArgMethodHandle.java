import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.List;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

public class VarArgMethodHandle {
  public static void main(String... args) throws Throwable {
    MethodHandle MH_asList = publicLookup().findStatic(Arrays.class,
                                                       "asList", methodType(List.class, Object[].class));
    System.out.println(MH_asList.invoke("one", "two").toString());
    System.out.println(MH_asList.invokeExact<warning descr="One argument is expected">("one", "two")</warning>.toString());
    System.out.println(MH_asList.invokeExact(new Object[] {"one", "two"}).toString());
    System.out.println(MH_asList.invoke(new Object[] {"one", "two"}).toString());
    MethodHandle MH_main = publicLookup().findStatic(VarArgMethodHandle.class,
                                                       "main", methodType(void.class, String[].class));
    System.out.println(MH_main.invoke("one", "two").toString());
    System.out.println(MH_main.invoke(new String[] {"one", "two"}).toString());
    System.out.println(MH_main.invoke(new Object[] {"one", "two"}).toString());
    System.out.println(MH_main.invoke(<warning descr="Argument is not assignable to 'java.lang.String'">1</warning>, <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>).toString());
    System.out.println(MH_main.invokeExact<warning descr="One argument is expected">("one", "two")</warning>.toString());
    System.out.println(MH_main.invokeExact<warning descr="One argument is expected">(1, 2)</warning>.toString());
    System.out.println(MH_main.invokeExact(new String[] {"one", "two"}).toString());
    System.out.println(MH_main.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String[]'">new Object[] {"one", "two"}</warning>).toString());
  }
}