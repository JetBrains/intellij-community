class C {
    void foo(){}
}

class D extends C{
    <error descr="'foo()' in 'D' clashes with 'foo()' in 'C'; both methods have same erasure, yet neither hides the other">static <T> void foo()</error>{}
}
