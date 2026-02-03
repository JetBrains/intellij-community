import javax.annotation.*;

@NonnullByDefault
class Test {
  @SuppressWarnings("NullAway.Init")
  Object member;
  
  Object <warning descr="@NonnullByDefault fields must be initialized">otherMember</warning>;

  private void accessMember() {
    member = new Object();
    otherMember = new String();
  }
}


@Nonnull
@javax.annotation.meta.TypeQualifierDefault(java.lang.annotation.ElementType.FIELD)
@interface NonnullByDefault {}