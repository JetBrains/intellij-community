class Test {
     void bar() {
       foo("");
     }
     static <T> void f<caret>oo(T t){System.out.println(t);}
}