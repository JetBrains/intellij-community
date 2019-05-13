@interface Example {
    <error descr="'myMethod()' is already defined in 'Example'">public String myMethod()</error>;
    <error descr="'myMethod()' is already defined in 'Example'">public int myMethod()</error>;
}