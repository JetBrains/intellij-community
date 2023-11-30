import javax.annotation.*;
import javax.annotation.meta.*;
import java.lang.annotation.*;
import org.jetbrains.annotations.NotNull;

import typeUse.Nullable;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.*;

@ObjectUtil.NotNullByDefault
final class ObjectUtil {
  @SafeVarargs
  public static <T> T[] deepCheckNotNull(@Nullable T @Nullable... varargs) {
    if (varargs == null) {
      throw new NullPointerException();
    }

    for (T element : varargs) {
      if (element == null) {
        throw new NullPointerException();
      }
    }
    return varargs;
  }

  @Documented
  @TypeQualifierDefault({ ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE_USE })
  @Retention(RetentionPolicy.CLASS)
  @Target(ElementType.TYPE)
  @Nonnull
  public @interface NotNullByDefault {
  }
}