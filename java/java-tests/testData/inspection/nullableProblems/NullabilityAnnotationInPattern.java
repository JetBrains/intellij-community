import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.List;

class NullabilityAnnotationInPattern {
  @Target(ElementType.TYPE_USE)
  @interface Marker {}

  record Rec(String value) {}

  record Box(List<String> list) {}

  void typeTestPattern(Object o) {
    if (o instanceof <warning descr="Nullability annotation is not applicable to pattern types">@Nullable</warning> String s) {
      System.out.println(s);
    }
  }

  void typeTestPatternTypeArgument(Collection<String> c) {
    if (c instanceof List<<warning descr="Nullability annotation is not applicable to pattern types">@Nullable</warning> String> l) {
      System.out.println(l);
    }
  }

  void deconstructionComponent(Object o) {
    if (o instanceof Rec(<warning descr="Nullability annotation is not applicable to pattern types">@NotNull</warning> String s)) {
      System.out.println(s);
    }
  }

  void deconstructionComponentTypeArgument(Object o) {
    if (o instanceof Box(List<<warning descr="Nullability annotation is not applicable to pattern types">@Nullable</warning> String> l)) {
      System.out.println(l);
    }
  }

  void switchPattern(Object o) {
    switch (o) {
      case Rec(<warning descr="Nullability annotation is not applicable to pattern types">@Nullable</warning> String s) -> System.out.println(s);
      case <warning descr="Nullability annotation is not applicable to pattern types">@NotNull</warning> Integer i -> System.out.println(i);
      default -> {}
    }
  }

  // A nullability annotation outside a pattern keeps its meaning.
  void notInPattern(@Nullable String s, List<@NotNull String> l) {
    if (s instanceof String other) {
      System.out.println(other + l);
    }
  }

  // An annotation that is not a nullability annotation says nothing about nullability, so a pattern may keep it.
  void unrelatedAnnotation(Object o, Collection<String> c) {
    if (o instanceof @Marker String s) {
      System.out.println(s);
    }
    if (c instanceof List<@Marker String> l) {
      System.out.println(l);
    }
    if (o instanceof Rec(@Marker String s)) {
      System.out.println(s);
    }
  }
}
