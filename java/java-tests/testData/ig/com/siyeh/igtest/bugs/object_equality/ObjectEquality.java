package com.siyeh.igtest.bugs.object_equality;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ObjectEquality {

  void test(boolean cond) { }

  void compareBoolean(boolean p1, boolean p2, Boolean w1, Boolean w2) {
    test(p1 == p2);
    test(p1 != p2);

    test(p1 == w2);
    test(w1 == p2);

    test(w1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> w2);
    test(w1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> w2);
  }

  // Numbers are handled separately by NumberEqualityInspection, so don't warn.
  void compareInt(int p1, int p2, Integer w1, Integer w2) {
    test(p1 == p2);
    test(p1 != p2);

    test(p1 == w2);
    test(w1 == p2);

    test(w1 == w2);
    test(w1 != w2);
  }

  // Numbers are handled separately by NumberEqualityInspection, so don't warn.
  void compareDouble(double p1, double p2, Double w1, Double w2) {
    test(p1 == p2);
    test(p1 != p2);

    test(p1 == w2);
    test(w1 == p2);

    test(w1 == w2);
    test(w1 != w2);
  }

  // Numbers are handled separately by NumberEqualityInspection, so don't warn.
  void compareNumbers(Double dbl, Integer i, Number num, Object obj) {
    test(<error descr="Operator '==' cannot be applied to 'java.lang.Double', 'java.lang.Integer'">dbl == i</error>);
    test(dbl == num);
    test(dbl == obj);
    test(i == num);
    test(i == obj);
    test(num == obj);
  }

  // Strings are handled separately by StringEqualityInspection, so don't warn.
  void compareStrings(String s1, String s2, Object obj) {
    test(s1 == s2);
    test(s1 != s2);
    test(s1 == obj);
    test(obj == s2);
  }

  // The interface 'java.util.Collection' does not require 'equals' and 'hashCode'
  // to implement a value-based comparison.
  void compareCollections(Collection<String> coll1, Collection<String> coll2) {
    test(coll1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> coll2);
    test(coll1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> coll2);
    test(coll1 == null);
    test(coll1 != null);
    test(null == coll2);
    test(null != coll2);
  }

  // The interface 'java.util.List' defines a contract for its 'equals' and 'hashCode' methods,
  // therefore comparing lists with '==' is unusual.
  void compareLists(List<String> list1, List<String> list2) {
    test(list1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> list2);
    test(list1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> list2);
    test(list1 == null);
    test(list1 != null);
    test(null == list2);
    test(null != list2);
  }

  // The interface 'java.util.Set' defines a contract for its 'equals' and 'hashCode' methods,
  // therefore comparing sets with '==' is unusual.
  void compareSets(Set<String> set1, Set<String> set2) {
    test(set1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> set2);
    test(set1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> set2);
    test(set1 == null);
    test(set1 != null);
    test(null == set2);
    test(null != set2);
  }

  // The interface 'java.util.Map' defines a contract for its 'equals' and 'hashCode' methods,
  // therefore comparing maps with '==' is unusual.
  void compareMaps(Map<String, Object> map1, Map<String, Object> map2) {
    test(map1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> map2);
    test(map1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> map2);
    test(null == map2);
    test(null != map2);
    test(map1 == null);
    test(map1 != null);
    test(map1 ==<error descr="Expression expected">)</error>;
  }

  // Enum constants are interned, therefore they can be safely compared using '=='.
  void compareEnums(MyEnum enum1, MyEnum enum2, Object obj) {
    test(enum1 == enum2);
    test(obj == MyEnum.FIRST);
  }

  // java.lang.Class is final and does not implement a custom 'equals',
  // therefore '==' and 'equals' are equivalent.
  void compareClasses(Class class1, Class class2) {
    test(class1 == class2);
    test(char.class == char.class);
  }

  // A class that has only private constructors
  // is a hint that objects of this class are interned,
  // thereby making '==' and 'equals' equivalent.
  void comparePrivateConstructor(MyPrivateConstructor priv1, MyPrivateConstructor priv2, Object obj) {
    test(priv1 == priv2);

    test(priv1 == obj);
    test(obj == priv1);
  }

  // Ensure that the inspection handles flipped operands in the same way.
  void compareOrder(MyFinal fin, MyClass cls, Object obj) {
    test(fin == obj);
    test(obj == fin);
    test(cls <warning descr="Object values are compared using '==', not 'equals()'">==</warning> obj);
    test(obj <warning descr="Object values are compared using '==', not 'equals()'">==</warning> cls);
  }

  // When comparing two expressions whose declared type is an interface,
  // it is generally unknown whether the dynamic types may be compared using '==' or not.
  void compareInterface(MyFinalInterface fini1, MyFinalInterface fini2, Object obj, MyFinal fin) {
    test(fini1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> fini2);

    test(fini1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> obj);
    test(obj <warning descr="Object values are compared using '==', not 'equals()'">==</warning> fini2);

    test(fini1 == fin);
    test(fin == fini2);
  }

  void comparePrivateConstructorInterface(MyPrivateConstructorsInterface pci1, MyPrivateConstructorsInterface pci2) {
    test(pci1 == pci2);
  }

  void incompatibleArguments(
    MyFinalInterface fini,
    MyUnimplementedInterface uni,
    MyNonFinal nfin,
    MyFinal fin
  ) {
    // There may be subclasses of MyNonFinal that implement MyFinalInterface.
    test(fini <warning descr="Object values are compared using '==', not 'equals()'">==</warning> nfin);

    test(uni == fini);

    test(<error descr="Operator '==' cannot be applied to 'com.siyeh.igtest.bugs.object_equality.ObjectEquality.MyUnimplementedInterface', 'com.siyeh.igtest.bugs.object_equality.ObjectEquality.MyFinal'">uni == fin</error>);
    test(<error descr="Operator '==' cannot be applied to 'com.siyeh.igtest.bugs.object_equality.ObjectEquality.MyFinal', 'com.siyeh.igtest.bugs.object_equality.ObjectEquality.MyUnimplementedInterface'">fin == uni</error>);
  }

  // An interface whose implementors are unknown.
  interface MyUnimplementedInterface {
  }

  // An interface whose (currently visible) implementors are all final.
  interface MyFinalInterface {
  }

  // An interface that is implement by a mixture of final and nonfinal types.
  interface MyMixedFinalInterface {
  }

  // All currently visible implementers of this interface have only private constructors,
  // which is a hint that objects of these classes are interned,
  // thereby making '==' and 'equals' equivalent.
  interface MyPrivateConstructorsInterface {
  }

  class MyClass {
  }

  enum MyEnum {
    FIRST, SECOND, THIRD;
  }

  class MyNonFinal implements MyMixedFinalInterface {
  }

  final class MyFinal implements MyFinalInterface, MyMixedFinalInterface {
  }

  class MyPrivateConstructor implements MyPrivateConstructorsInterface {
    private MyPrivateConstructor() { }
  }

  class MyEnumTypeParameter<E extends Enum<E>> {
    void testTypeParameter(E a, E b) {
      test(a == b);
    }
  }

  class MyIdentity {
    @Override
    public boolean equals(Object obj) {
      // In the 'equals' method, comparing 'this' by reference allowed,
      // as it is a concious implementation choice.
      return this == obj || obj == this;
    }

    // In methods other than 'equals', comparing 'this' by reference is suspicious.
    void notEquals(Object obj) {
      test(this <warning descr="Object values are compared using '==', not 'equals()'">==</warning> obj);
      test(obj <warning descr="Object values are compared using '==', not 'equals()'">==</warning> this);
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  class MyBean {
    private Boolean field;

    @Override
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) return false;
      MyBean other = (MyBean)obj;
      // In the 'equals' method, comparing fields is no different than anywhere else.
      return field <warning descr="Object values are compared using '==', not 'equals()'">==</warning> other.field;
    }

    @Override
    public int hashCode() {
      return field.hashCode();
    }
  }
}