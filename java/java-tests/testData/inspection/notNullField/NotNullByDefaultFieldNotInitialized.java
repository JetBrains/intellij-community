import javax.annotation.*;

@NonnullByDefault
class Test {
  Object <warning descr="@NonnullByDefault fields must be initialized">member</warning>;

  private void accessMember() {
    member = new Object();
  }
}


@Nonnull
@javax.annotation.meta.TypeQualifierDefault(java.lang.annotation.ElementType.FIELD)
@interface NonnullByDefault {}