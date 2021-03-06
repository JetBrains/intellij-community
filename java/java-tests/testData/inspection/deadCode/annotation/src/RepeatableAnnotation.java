import java.lang.annotation.*;

@interface Container1 {
  Repeatable1[] value();
}

@Repeatable(Container1.class)
@interface Repeatable1 {
}

@interface Container2 {
  Repeatable2[] value();
}

@Target(ElementType.TYPE)
@Repeatable(Container2.class)
@interface Repeatable2 { // malformed annotation as container's target is not subset of the current target
}

@interface Container3 {
  Repeatable3[] value();
}

@Repeatable(Container3.class)
@interface Repeatable3 {
}

@Repeatable2
class Annotated1 {
  @Repeatable1
  public static void main(String[] args) {
  }
}

class Annotated1 {
  public static void main(String[] args) {
  }
}


