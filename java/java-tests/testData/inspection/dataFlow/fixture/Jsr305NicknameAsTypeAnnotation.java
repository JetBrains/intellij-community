import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import javax.annotation.meta.TypeQualifierNickname;
import javax.annotation.meta.When;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Nonnull(when = When.ALWAYS)
@TypeQualifierNickname
@Target({ElementType.TYPE})
@TypeQualifierDefault({
  ElementType.METHOD,
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.LOCAL_VARIABLE,
  ElementType.TYPE_PARAMETER,
  ElementType.TYPE_USE,
})
@interface DefaultNonNull {
}

@Nonnull(when = When.MAYBE)
@TypeQualifierNickname
@Target({
  ElementType.METHOD,
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.LOCAL_VARIABLE,
  ElementType.TYPE_USE,
})
@interface Nullable {
}

@DefaultNonNull
class Test {
  void test(String s) {
    if (<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {}
    @Nullable String @Nullable [] arr = new String[] {"a", "b", "c", <warning descr="'null' is stored to an array of @NotNull elements">null</warning>};
    @Nullable String @Nullable [] arr2 = {"a", "b", "c", null};
    @Nullable String @Nullable [] arr3 = new @Nullable String [] {"a", "b", "c", null};
  }
}
