public class UncheckedWarningWithCast{
  interface NotGenericInterface {
  }

  interface GenericInterface<T> {
  }

  interface ChildGenericInterface<T> extends GenericInterface<T> {
  }

  interface NotChildGenericInterface<T> {
  }

  interface ChildNotRelatedGenericInterface<T> extends GenericInterface<CharSequence> {
  }

  interface ChildNotGeneric extends GenericInterface<CharSequence> {
  }

  interface NotChildNotGeneric {
  }

  final class NotChildNotGenericClass {
  }

  final class ChildNotGenericClass implements GenericInterface<CharSequence> {
  }

  class ChildNotGenericClassNotCompatible implements GenericInterface<Integer> {
  }
  class ChildNotGenericNotFinalClassNotCompatible implements GenericInterface<Number> {
  }

  final class ChildGenericClass<T> implements GenericInterface<T> {
  }


  public interface Function<T, R> {
    R apply(T t);
  }
  public interface Function2<T, R> extends Function<T, R> {
  }
  public interface Function3<T, R> extends Function2<T, R> {
  }

  public interface UnaryOperator<T> extends Function<T, T> {
  }
  public interface UnaryOperator2<T, R> extends Function<T, T> {
  }

  class ExtendFunction<T> implements Function<CharSequence, T>{

    @Override
    public T apply(CharSequence charSequence) {
      return null;
    }
  }

  class ClassMapper<T> implements UnaryOperator<T> {
    private T in;
    private T out;

    ClassMapper(T in, T out) {
      this.in = in;
      this.out = out;
    }

    public T apply(T arg) {
      return in.equals(arg) ? out : null;
    }
  }

  void test(UnaryOperator<? extends CharSequence> op, Object ob) {
    //Changed Example 18.5.5-1. Record Pattern Type Inference JLS with Record Patterns (Second Preview)
    ClassMapper<? extends CharSequence> seq = (ClassMapper<? extends CharSequence>) op;
    //unchecked
    ClassMapper<? extends String> seq2 = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.UnaryOperator<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ClassMapper<? extends java.lang.String>'">(ClassMapper<? extends String>) op</warning>;
  }

  <T extends CharSequence> void test2(UnaryOperator<T> op, Object ob) {
    ClassMapper<? extends CharSequence> seq = (ClassMapper<? extends CharSequence>) op;
  }
  void test3(Function<? extends String, ? extends String> op, Object ob) {
    //unchecked
    ClassMapper<? extends CharSequence> seq = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.Function<capture<? extends java.lang.String>,capture<? extends java.lang.String>>' to 'UncheckedWarningWithCast.ClassMapper<? extends java.lang.CharSequence>'">(ClassMapper<? extends CharSequence>) op</warning>;
    //unchecked
    UnaryOperator<? extends CharSequence> seq2 = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.Function<capture<? extends java.lang.String>,capture<? extends java.lang.String>>' to 'UncheckedWarningWithCast.UnaryOperator<? extends java.lang.CharSequence>'">(UnaryOperator<? extends CharSequence>) op</warning>;
    //unchecked
    UnaryOperator2<? extends CharSequence, ? extends CharSequence> seq3 = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.Function<capture<? extends java.lang.String>,capture<? extends java.lang.String>>' to 'UncheckedWarningWithCast.UnaryOperator2<? extends java.lang.CharSequence,? extends java.lang.CharSequence>'">(UnaryOperator2<? extends CharSequence, ? extends CharSequence>) op</warning>;
  }
  void test4(Function<? extends String, ? extends String> op, Object ob) {
    Function3<? extends CharSequence, ? extends CharSequence> seq = (Function3<? extends CharSequence, ? extends CharSequence>) op;
  }

  void test5(Function<CharSequence, ? extends CharSequence> op, Object ob) {
    ExtendFunction<? extends CharSequence> seq = (ExtendFunction<? extends CharSequence>) op;
  }

  void test6(Function<? extends CharSequence, ? extends CharSequence> op, Object ob) {
    //unchecked
    ExtendFunction<? extends CharSequence> seq = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.Function<capture<? extends java.lang.CharSequence>,capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ExtendFunction<? extends java.lang.CharSequence>'">(ExtendFunction<? extends CharSequence>) op</warning>;
  }

  void test7(Function<? extends String, ? extends String> op, Object ob) {
    Function<? extends CharSequence, ? extends CharSequence> seq = (Function<? extends CharSequence, ? extends CharSequence>) op;
  }

  void test8(UnaryOperator<? extends String> op, Object ob) {
    ClassMapper<? extends CharSequence> seq = (ClassMapper<? extends CharSequence>) op;
    ClassMapper<? extends String> seq2 = (ClassMapper<? extends String>) op;
  }

  public void testChildGenericInterface(GenericInterface raw,
                                        GenericInterface<? super String> superGeneric,
                                        GenericInterface<? extends CharSequence> extendGeneric,
                                        NotGenericInterface notGenericInterface) {
    //unchecked
    ChildGenericInterface<String> notRaw = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface' to 'UncheckedWarningWithCast.ChildGenericInterface<java.lang.String>'">(ChildGenericInterface<String>) raw</warning>;
    ChildGenericInterface<?> superWildcardGenericCast = (ChildGenericInterface<?>) superGeneric;
    ChildGenericInterface<?> extendWildcardGenericCast = (ChildGenericInterface<?>) extendGeneric;
    ChildGenericInterface<? super String> superEqualGenericCast = (ChildGenericInterface<? super String>) superGeneric;
    ChildGenericInterface<? extends CharSequence> extendEqualGenericCast = (ChildGenericInterface<? extends CharSequence>) extendGeneric;
    //unchecked
    ChildGenericInterface<Object> superUpperBoundGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildGenericInterface<java.lang.Object>'">(ChildGenericInterface<Object>) superGeneric</warning>;
    //unchecked
    ChildGenericInterface<CharSequence> extendUpperBoundGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ChildGenericInterface<java.lang.CharSequence>'">(ChildGenericInterface<CharSequence>) extendGeneric</warning>;
    //unchecked
    ChildGenericInterface<? super Integer> superNotRelatedGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildGenericInterface<? super java.lang.Integer>'">(ChildGenericInterface<? super Integer>) superGeneric</warning>;
    //unchecked
    ChildGenericInterface<? extends String> extendNotCoveredGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ChildGenericInterface<? extends java.lang.String>'">(ChildGenericInterface<? extends String>) extendGeneric</warning>;
    //unchecked
    ChildGenericInterface<? super Integer> superNotGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.ChildGenericInterface<? super java.lang.Integer>'">(ChildGenericInterface<? super Integer>) notGenericInterface</warning>;
    //unchecked
    ChildGenericInterface<? extends String> extendNotGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.ChildGenericInterface<? extends java.lang.String>'">(ChildGenericInterface<? extends String>) notGenericInterface</warning>;
  }

  public void testChildGenericClass(GenericInterface raw,
                                    GenericInterface<? super String> superGeneric,
                                    GenericInterface<? extends CharSequence> extendGeneric,
                                    NotGenericInterface notGenericInterface) {
    //unchecked
    ChildGenericClass<String> notRaw = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface' to 'UncheckedWarningWithCast.ChildGenericClass<java.lang.String>'">(ChildGenericClass<String>) raw</warning>;
    ChildGenericClass<?> superWildcardGenericCast = (ChildGenericClass<?>) superGeneric;
    ChildGenericClass<?> extendWildcardGenericCast = (ChildGenericClass<?>) extendGeneric;
    ChildGenericClass<? super String> superEqualGenericCast = (ChildGenericClass<? super String>) superGeneric;
    ChildGenericClass<? extends CharSequence> extendEqualGenericCast = (ChildGenericClass<? extends CharSequence>) extendGeneric;
    //unchecked
    ChildGenericClass<Object> superUpperBoundGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildGenericClass<java.lang.Object>'">(ChildGenericClass<Object>) superGeneric</warning>;
    //unchecked
    ChildGenericClass<CharSequence> extendUpperBoundGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ChildGenericClass<java.lang.CharSequence>'">(ChildGenericClass<CharSequence>) extendGeneric</warning>;
    //unchecked
    ChildGenericClass<? super Integer> superNotRelatedGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildGenericClass<? super java.lang.Integer>'">(ChildGenericClass<? super Integer>) superGeneric</warning>;
    //unchecked
    ChildGenericClass<? extends String> extendNotCoveredGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ChildGenericClass<? extends java.lang.String>'">(ChildGenericClass<? extends String>) extendGeneric</warning>;
    //not compile
    ChildGenericClass<? super Integer> superNotGenericCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.ChildGenericClass<? super java.lang.Integer>'">(ChildGenericClass<? super Integer>) notGenericInterface</error>;
    //not compile
    ChildGenericClass<? extends String> extendNotGenericCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.ChildGenericClass<? extends java.lang.String>'">(ChildGenericClass<? extends String>) notGenericInterface</error>;
  }

  public void testNotChildGenericInterface(GenericInterface raw,
                                           GenericInterface<? super String> superGeneric,
                                           GenericInterface<? extends CharSequence> extendGeneric,
                                           NotGenericInterface notGenericInterface) {
    //unchecked
    NotChildGenericInterface<String> notRaw = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface' to 'UncheckedWarningWithCast.NotChildGenericInterface<java.lang.String>'">(NotChildGenericInterface<String>) raw</warning>;
    NotChildGenericInterface<?> superWildcardGenericCast = (NotChildGenericInterface<?>) superGeneric;
    NotChildGenericInterface<?> extendWildcardGenericCast = (NotChildGenericInterface<?>) extendGeneric;
    //unchecked
    NotChildGenericInterface<? super String> superEqualGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.NotChildGenericInterface<? super java.lang.String>'">(NotChildGenericInterface<? super String>) superGeneric</warning>;
    //unchecked
    NotChildGenericInterface<? extends CharSequence> extendEqualGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.NotChildGenericInterface<? extends java.lang.CharSequence>'">(NotChildGenericInterface<? extends CharSequence>) extendGeneric</warning>;
    //unchecked
    NotChildGenericInterface<Object> superUpperBoundGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.NotChildGenericInterface<java.lang.Object>'">(NotChildGenericInterface<Object>) superGeneric</warning>;
    //unchecked
    NotChildGenericInterface<CharSequence> extendUpperBoundGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.NotChildGenericInterface<java.lang.CharSequence>'">(NotChildGenericInterface<CharSequence>) extendGeneric</warning>;
    //unchecked
    NotChildGenericInterface<? super Integer> superNotRelatedGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.NotChildGenericInterface<? super java.lang.Integer>'">(NotChildGenericInterface<? super Integer>) superGeneric</warning>;
    //unchecked
    NotChildGenericInterface<? extends String> extendNotCoveredGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.NotChildGenericInterface<? extends java.lang.String>'">(NotChildGenericInterface<? extends String>) extendGeneric</warning>;
    //unchecked
    NotChildGenericInterface<? super Integer> superNotGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.NotChildGenericInterface<? super java.lang.Integer>'">(NotChildGenericInterface<? super Integer>) notGenericInterface</warning>;
    //unchecked
    NotChildGenericInterface<? extends String> extendNotGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.NotChildGenericInterface<? extends java.lang.String>'">(NotChildGenericInterface<? extends String>) notGenericInterface</warning>;
  }

  public void testChildNotRelatedGenericInterface(GenericInterface raw,
                                                  GenericInterface<? super String> superGeneric,
                                                  GenericInterface<? extends CharSequence> extendGeneric,
                                                  NotGenericInterface notGenericInterface) {
    //unchecked
    ChildNotRelatedGenericInterface<String> notRaw = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<java.lang.String>'">(ChildNotRelatedGenericInterface<String>) raw</warning>;
    ChildNotRelatedGenericInterface<?> superWildcardGenericCast = (ChildNotRelatedGenericInterface<?>) superGeneric;
    ChildNotRelatedGenericInterface<?> extendWildcardGenericCast = (ChildNotRelatedGenericInterface<?>) extendGeneric;
    //unchecked
    ChildNotRelatedGenericInterface<? super String> superEqualGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<? super java.lang.String>'">(ChildNotRelatedGenericInterface<? super String>) superGeneric</warning>;
    //unchecked
    ChildNotRelatedGenericInterface<? extends CharSequence> extendEqualGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<? extends java.lang.CharSequence>'">(ChildNotRelatedGenericInterface<? extends CharSequence>) extendGeneric</warning>;
    //unchecked
    ChildNotRelatedGenericInterface<Object> superUpperBoundGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<java.lang.Object>'">(ChildNotRelatedGenericInterface<Object>) superGeneric</warning>;
    //unchecked
    ChildNotRelatedGenericInterface<CharSequence> extendUpperBoundGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<java.lang.CharSequence>'">(ChildNotRelatedGenericInterface<CharSequence>) extendGeneric</warning>;
    //unchecked
    ChildNotRelatedGenericInterface<? super Integer> superNotRelatedGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<? super java.lang.Integer>'">(ChildNotRelatedGenericInterface<? super Integer>) superGeneric</warning>;
    //unchecked
    ChildNotRelatedGenericInterface<? extends String> extendNotCoveredGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<? extends java.lang.String>'">(ChildNotRelatedGenericInterface<? extends String>) extendGeneric</warning>;
    //unchecked
    ChildNotRelatedGenericInterface<? super Integer> superNotGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<? super java.lang.Integer>'">(ChildNotRelatedGenericInterface<? super Integer>) notGenericInterface</warning>;
    //unchecked
    ChildNotRelatedGenericInterface<? extends String> extendNotGenericCast = <warning descr="Unchecked cast: 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.ChildNotRelatedGenericInterface<? extends java.lang.String>'">(ChildNotRelatedGenericInterface<? extends String>) notGenericInterface</warning>;
  }

  public void testChildNotGeneric(GenericInterface raw,
                                  GenericInterface<? super String> superGeneric,
                                  GenericInterface<? extends CharSequence> extendGeneric,
                                  GenericInterface<? super Integer> superGenericInteger,
                                  GenericInterface<? extends Integer> extendGenericInteger,
                                  NotGenericInterface notGenericInterface) {
    ChildNotGeneric notRaw = (ChildNotGeneric) raw;
    ChildNotGeneric superCast = (ChildNotGeneric) superGeneric;
    ChildNotGeneric extendCast = (ChildNotGeneric) extendGeneric;
    //not compile
    ChildNotGeneric extendNumberCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.Integer>>' to 'UncheckedWarningWithCast.ChildNotGeneric'">(ChildNotGeneric) extendGenericInteger</error>;
    //not compile
    ChildNotGeneric superNumberCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.Integer>>' to 'UncheckedWarningWithCast.ChildNotGeneric'">(ChildNotGeneric) superGenericInteger</error>;
    ChildNotGeneric superNotGenericCast = (ChildNotGeneric) notGenericInterface;
    ChildNotGeneric extendNotGenericCast = (ChildNotGeneric) notGenericInterface;
  }

  public void testNotChildNotGeneric(GenericInterface raw,
                                     GenericInterface<? super String> superGeneric,
                                     GenericInterface<? extends CharSequence> extendGeneric,
                                     GenericInterface<? super Integer> superGenericInteger,
                                     GenericInterface<? extends Integer> extendGenericInteger,
                                     NotGenericInterface notGenericInterface) {
    NotChildNotGeneric notRaw = (NotChildNotGeneric) raw;
    NotChildNotGeneric superCast = (NotChildNotGeneric) superGeneric;
    NotChildNotGeneric extendCast = (NotChildNotGeneric) extendGeneric;
    NotChildNotGeneric extendNumberCast = (NotChildNotGeneric) extendGenericInteger;
    NotChildNotGeneric superNumberCast = (NotChildNotGeneric) superGenericInteger;
    NotChildNotGeneric superNotGenericCast = (NotChildNotGeneric) notGenericInterface;
    NotChildNotGeneric extendNotGenericCast = (NotChildNotGeneric) notGenericInterface;
  }

  public void testNotChildNotGenericClass(GenericInterface raw,
                                          GenericInterface<? super String> superGeneric,
                                          GenericInterface<? extends CharSequence> extendGeneric,
                                          GenericInterface<? super Integer> superGenericInteger,
                                          GenericInterface<? extends Integer> extendGenericInteger,
                                          NotGenericInterface notGenericInterface) {
    //not compile
    NotChildNotGenericClass notRaw = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface' to 'UncheckedWarningWithCast.NotChildNotGenericClass'">(NotChildNotGenericClass) raw</error>;
    //not compile
    NotChildNotGenericClass superCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.NotChildNotGenericClass'">(NotChildNotGenericClass) superGeneric</error>;
    //not compile
    NotChildNotGenericClass extendCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.NotChildNotGenericClass'">(NotChildNotGenericClass) extendGeneric</error>;
    //not compile
    NotChildNotGenericClass extendNumberCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.Integer>>' to 'UncheckedWarningWithCast.NotChildNotGenericClass'">(NotChildNotGenericClass) extendGenericInteger</error>;
    //not compile
    NotChildNotGenericClass superNumberCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.Integer>>' to 'UncheckedWarningWithCast.NotChildNotGenericClass'">(NotChildNotGenericClass) superGenericInteger</error>;
    //not compile
    NotChildNotGenericClass superNotGenericCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.NotChildNotGenericClass'">(NotChildNotGenericClass) notGenericInterface</error>;
    //not compile
    NotChildNotGenericClass extendNotGenericCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.NotChildNotGenericClass'">(NotChildNotGenericClass) notGenericInterface</error>;
  }

  public void testChildNotGenericClass(GenericInterface raw,
                                       GenericInterface<? super String> superGeneric,
                                       GenericInterface<? extends CharSequence> extendGeneric,
                                       GenericInterface<? super Integer> superGenericInteger,
                                       GenericInterface<? extends Integer> extendGenericInteger,
                                       NotGenericInterface notGenericInterface) {
    ChildNotGenericClass notRaw = (ChildNotGenericClass) raw;
    ChildNotGenericClass superCast = (ChildNotGenericClass) superGeneric;
    ChildNotGenericClass extendCast = (ChildNotGenericClass) extendGeneric;
    //not compile
    ChildNotGenericClass extendNumberCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.Integer>>' to 'UncheckedWarningWithCast.ChildNotGenericClass'">(ChildNotGenericClass) extendGenericInteger</error>;
    //not compile
    ChildNotGenericClass superNumberCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.Integer>>' to 'UncheckedWarningWithCast.ChildNotGenericClass'">(ChildNotGenericClass) superGenericInteger</error>;
    //not compile
    ChildNotGenericClass superNotGenericCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.ChildNotGenericClass'">(ChildNotGenericClass) notGenericInterface</error>;
    //not compile
    ChildNotGenericClass extendNotGenericCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.NotGenericInterface' to 'UncheckedWarningWithCast.ChildNotGenericClass'">(ChildNotGenericClass) notGenericInterface</error>;
  }

  public void testChildGenericNotCompatibleClass(GenericInterface raw,
                                                 GenericInterface<? super String> superGeneric,
                                                 GenericInterface<? extends CharSequence> extendGeneric,
                                                 GenericInterface<? super Integer> superGenericInteger,
                                                 GenericInterface<? extends Integer> extendGenericInteger,
                                                 NotGenericInterface notGenericInterface) {
    ChildNotGenericClassNotCompatible notRaw = (ChildNotGenericClassNotCompatible) raw;
    //not compile
    ChildNotGenericClassNotCompatible superCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildNotGenericClassNotCompatible'">(ChildNotGenericClassNotCompatible) superGeneric</error>;
    //not compile
    ChildNotGenericClassNotCompatible extendCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? extends java.lang.CharSequence>>' to 'UncheckedWarningWithCast.ChildNotGenericClassNotCompatible'">(ChildNotGenericClassNotCompatible) extendGeneric</error>;
    ChildNotGenericClassNotCompatible extendNumberCast = (ChildNotGenericClassNotCompatible) extendGenericInteger;
    ChildNotGenericClassNotCompatible superNumberCast = (ChildNotGenericClassNotCompatible) superGenericInteger;
    ChildNotGenericClassNotCompatible superNotGenericCast = (ChildNotGenericClassNotCompatible) notGenericInterface;
    ChildNotGenericClassNotCompatible extendNotGenericCast = (ChildNotGenericClassNotCompatible) notGenericInterface;
  }
  public void testChildGenericNotFinalNotCompatibleClass(GenericInterface raw,
                                                         GenericInterface<? super String> superGeneric,
                                                         GenericInterface<? extends CharSequence> extendGeneric,
                                                         GenericInterface<? super Integer> superGenericInteger,
                                                         GenericInterface<? extends Integer> extendGenericInteger,
                                                         NotGenericInterface notGenericInterface) {
    ChildNotGenericNotFinalClassNotCompatible notRaw = (ChildNotGenericNotFinalClassNotCompatible) raw;
    //not compile
    ChildNotGenericNotFinalClassNotCompatible superCast = <error descr="Inconvertible types; cannot cast 'UncheckedWarningWithCast.GenericInterface<capture<? super java.lang.String>>' to 'UncheckedWarningWithCast.ChildNotGenericNotFinalClassNotCompatible'">(ChildNotGenericNotFinalClassNotCompatible) superGeneric</error>;

    ChildNotGenericNotFinalClassNotCompatible superNumberCast = (ChildNotGenericNotFinalClassNotCompatible) superGenericInteger;
    ChildNotGenericNotFinalClassNotCompatible superNotGenericCast = (ChildNotGenericNotFinalClassNotCompatible) notGenericInterface;
    ChildNotGenericNotFinalClassNotCompatible extendNotGenericCast = (ChildNotGenericNotFinalClassNotCompatible) notGenericInterface;
  }

  static class ClassMapperNumber implements UnaryOperator<Number> {
    private Number in;
    private Number out;

    ClassMapperNumber(Number in, Number out) {
      this.in = in;
      this.out = out;
    }

    public Number apply(Number arg) {
      return in.equals(arg) ? out : null;
    }

  }
}