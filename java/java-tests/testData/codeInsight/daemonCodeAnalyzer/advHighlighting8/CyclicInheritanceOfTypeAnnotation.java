// cyclic inhertiance

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;


interface Interface {
}

<error descr="Cyclic inheritance involving 'Clazz'">class Clazz implements @Clazz.Annotation Interface</error> {
  @Target(ElementType.TYPE_USE)
  @interface Annotation {
  }
}