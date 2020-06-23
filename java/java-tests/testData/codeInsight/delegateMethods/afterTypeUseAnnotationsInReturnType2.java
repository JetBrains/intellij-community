
import java.lang.annotation.*;
import java.util.Map;

@Target({ElementType.METHOD, ElementType.TYPE_USE})
@interface Foo {}

interface X {
  @Foo Map.@Foo Entry getString();
}

static class Y implements X {
  X x;

    @Override
    @Foo
    public Map.@Foo Entry getString() {
        return x.getString();
    }
}
