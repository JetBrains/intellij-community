import java.lang.reflect.*;

class Example {
    private void demo() {
        <error descr="Cannot resolve symbol 'TypeVariable'">TypeVariable</error><Class<? extends Example>>[]  typeParameters  =  getClass().getTypeParameters();
    }
}
