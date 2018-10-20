import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// IDEABKL-7233
public class XorNullity {
  void test(CreateForm createForm) {
    if(createForm.getOpenIdIdentity() == null ^ createForm.getOpenIdProvider() == null) {
      throw new RuntimeException("Invalid request");
    }

    if(createForm.getOpenIdIdentity() != null) {
      findByOpenIdIdentity(createForm.getOpenIdProvider()); // never null
    }
  }

  void test2(CreateForm createForm) {
    if(createForm.getOpenIdIdentity() == null ^ createForm.getOpenIdProvider() != null) {
      throw new RuntimeException("Invalid request");
    }

    if(createForm.getOpenIdIdentity() != null) {
      findByOpenIdIdentity(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">createForm.getOpenIdProvider()</warning>); // nullable
    }
  }

  void findByOpenIdIdentity(@NotNull Object identity) {}

  interface CreateForm {
    @Nullable
    @Contract(pure = true)
    Object getOpenIdIdentity();

    @Nullable
    @Contract(pure = true)
    Object getOpenIdProvider();
  }
}
