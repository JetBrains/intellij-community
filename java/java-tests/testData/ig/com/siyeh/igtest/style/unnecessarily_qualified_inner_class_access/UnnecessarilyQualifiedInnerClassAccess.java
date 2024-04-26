package com.siyeh.igtest.style.unnecessarily_qualified_inner_class_access;
import java.util.Map;

@Y(<warning descr="'X' is unnecessarily qualified with 'UnnecessarilyQualifiedInnerClassAccess'">UnnecessarilyQualifiedInnerClassAccess</warning>.X.class)
public class UnnecessarilyQualifiedInnerClassAccess<T> {

    public UnnecessarilyQualifiedInnerClassAccess(int i) {
        <warning descr="'Entry' is unnecessarily qualified with 'Map'">Map<caret></warning>.Entry entry;
    }

    public UnnecessarilyQualifiedInnerClassAccess() {
        final String test =  <warning descr="'Inner' is unnecessarily qualified with 'UnnecessarilyQualifiedInnerClassAccess'">UnnecessarilyQualifiedInnerClassAccess</warning> .Inner.TEST;
    }
    public static class Inner {
        public static final String TEST = "test";
    }

    void foo() {
        UnnecessarilyQualifiedInnerClassAccess<String>.X x; // no warning here, because generic parameter is needed
         <warning descr="'Y' is unnecessarily qualified with 'UnnecessarilyQualifiedInnerClassAccess'">UnnecessarilyQualifiedInnerClassAccess</warning> .Y<String> y;
    }

    class X {
        T t;
    }

    static class Y<T> {
        T t;
    }

  static void x() {
    class A {
      class B {
        class C {}
      }
    }
    A.B.C c;
  }
}
@interface Y {
    Class value();
}

class Foo extends PresenterWidget<<warning descr="'Bar' is unnecessarily qualified with 'Foo'">Foo</warning>.Bar> { // warning because Bar can be imported
    interface Bar extends View { }
}

interface View {}
class PresenterWidget<T>{}

class MultipleInheritance {
  interface I1 {
    interface V {
    }

    String FOO = "";
  }

  interface I2 {
    interface V {
    }

    String FOO = "";
  }

  static class C implements I1, I2 {
    public I1.V foo() {
      System.out.println(I1.FOO);
      return null;
    }
  }
}
@TestAnnotation(<warning descr="'TestInner' is unnecessarily qualified with 'TestOuter'">TestOuter</warning>.TestInner.TEST_FIELD)
class TestOuter {
  public interface TestInner {
    String TEST_FIELD = "TEST";
  }
}
@interface TestAnnotation {
  String value();
}

class HiearchyWithDefaults {

    public interface UnmodifiableCollection<E> extends Iterable<E> {
        boolean contains(E element);
        interface Defaults<E> {}

    }

    public interface UnmodifiableList<E> extends UnmodifiableCollection<E> {

        interface Decorator<E> extends Defaults<E> {
            @SuppressWarnings("RedundantMethodOverride")
            //IntellijIdea blooper: "method is identical to its super method" (and "redundant suppression")
            @Override
            default boolean contains(E element) {
                //IntellijIdea blooper: "Unnecessarily Qualified Inner Class Access"
                return UnmodifiableList.Defaults.super.contains(element);
            }

        }

        interface Defaults<E> extends UnmodifiableList<E>, UnmodifiableCollection.Defaults<E> {
            @Override
            default boolean contains(E element) {
                return false;
            }

        }
    }

}

/**
 * {@link java.util.concurrent.ConcurrentHashMap.SearchValuesTask}
 */
class InaccessibleClassReferencedInsideJavadocLink { }
class RecordQualifierInside {

  private record CalcResult(<warning descr="'Inside' is unnecessarily qualified with 'CalcResult'" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES">CalcResult</warning>.Inside.Type type) {


    private record Inside(){
      enum Type {
        OK, ERROR
      }
    }

  }
}