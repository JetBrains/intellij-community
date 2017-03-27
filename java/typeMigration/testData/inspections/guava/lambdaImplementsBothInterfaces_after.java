import com.google.common.base.Function;

import java.util.Set;
import java.util.stream.Collectors;

class Test {
  public interface HasCode {
    String getCode();
  }

  public enum GetCode implements Function<HasCode, String>, java.util.function.Function<HasCode, String> {
    FUNC;

    @Override
    public String apply(HasCode e) {
      return e.getCode();
    }

  }

  public Set<String> getRegionCodeList(Set<HasCode> regions) {
    return regions.stream().map(GetCode.FUNC).collect(Collectors.toSet());
  }
}