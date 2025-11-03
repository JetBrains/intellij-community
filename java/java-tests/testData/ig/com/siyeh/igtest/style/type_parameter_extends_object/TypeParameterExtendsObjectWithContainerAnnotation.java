import org.jspecify.annotations.NullMarked;

class TypeParameterExtendsObjectWithContainerAnnotation {
  @NullMarked
  interface SuperSuper {
    Lib<?> t();

    void checkNeverNull(Lib<? extends Object> lib);
  }
}

@NullMarked
final class Inner1<<warning descr="Type parameter 'T' explicitly extends 'java.lang.Object'">T</warning> extends Object> {
}

class Lib<L> {
}