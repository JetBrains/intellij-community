@Deprecated @SuppressWarnings("")
<error descr="Class 'Foo' must either be declared abstract or implement abstract method 'run()' in 'Runnable'">public class Foo implements Runnable</error> {

}

class F {
  @Deprecated @SuppressWarnings("") <error descr="'f()' is already defined in 'F'">void f()</error> {}
  @Deprecated @SuppressWarnings("") <error descr="'f()' is already defined in 'F'">void f()</error> {}
}
