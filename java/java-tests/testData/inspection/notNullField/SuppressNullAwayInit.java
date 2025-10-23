import javax.annotation.*;

@NonnullByDefault
class Test {
  @SuppressWarnings("NullAway.Init")
  Object member;

  private void accessMember() {
    member = new Object();
  }
}


@Nonnull
@javax.annotation.meta.TypeQualifierDefault(java.lang.annotation.ElementType.FIELD)
@interface NonnullByDefault {}