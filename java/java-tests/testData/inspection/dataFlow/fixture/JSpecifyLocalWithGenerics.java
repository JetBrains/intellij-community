import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

@NullMarked
class LocalsWithGenericsNullness {
  public <R extends Serializable> @Nullable R fetchOne() {
    throw new UnsupportedOperationException("Not relevant");
  }

  public <R extends Serializable> void fetchOneDelegate() {
    R record = fetchOne(); 
  }
}