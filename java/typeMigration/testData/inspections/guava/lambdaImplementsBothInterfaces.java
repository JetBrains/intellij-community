import com.google.common.collect.FluentIterable;

import java.util.Set;

class Test {
  public interface HasCode {
    String getCode();
  }

  public enum GetCode implements com.google.common.base.Function<HasCode, String>, java.util.function.Function<HasCode, String> {
    FUNC;

    @Override
    public String apply(HasCode e) {
      return e.getCode();
    }

  }

  public Set<String> getRegionCodeList(Set<HasCode> regions) {
    return FluentIterable.f<caret>rom(regions).transform(GetCode.FUNC::apply).toSet();
  }
}