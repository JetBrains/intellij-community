import java.lang.annotation.*;

record CStyle(int a<error descr="C-style record component declaration is not allowed">[]</error>) {}
record CStyle2(int[] a<error descr="C-style record component declaration is not allowed">[] []</error> ) {}
record JavaStyle(int[] [] a) {}