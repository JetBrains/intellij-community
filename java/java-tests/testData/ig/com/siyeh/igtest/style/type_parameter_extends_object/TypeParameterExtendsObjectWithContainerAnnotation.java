import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

class TypeParameterExtendsObjectWithContainerAnnotation {
  @NullMarked
  interface SuperSuper {
    Lib<?> t();

    void checkNeverNull(Lib<? extends Object> lib);

    @NullUnmarked
    void checkNeverNull2(Lib<<warning descr="Wildcard type argument '?' explicitly extends 'java.lang.Object'">?</warning> extends Object> lib);
  }
}

@NullMarked
final class Inner1<<warning descr="Type parameter 'T' explicitly extends 'java.lang.Object'">T</warning> extends Object> {
}

class Lib<L> {
}