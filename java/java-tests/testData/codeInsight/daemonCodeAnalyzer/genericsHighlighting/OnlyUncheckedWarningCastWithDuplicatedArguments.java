public class OnlyUncheckedWarningCastWithDuplicatedArguments{
  public static void test(BiGenericInterface<? extends String, ? extends String> t,
                          EmptyInterface emptyInterface,
                          BiGenericInterface<String, String> stringI) {
    //unchecked
    UnaryGenericInterface<? extends CharSequence> t1 = <warning descr="Unchecked cast: 'BiGenericInterface<capture<? extends java.lang.String>,capture<? extends java.lang.String>>' to 'UnaryGenericInterface<? extends java.lang.CharSequence>'">(UnaryGenericInterface<? extends CharSequence>)t</warning>;
    //unchecked
    UnaryGenericInterface<? extends CharSequence> t2 = <warning descr="Unchecked cast: 'EmptyInterface' to 'UnaryGenericInterface<? extends java.lang.CharSequence>'">(UnaryGenericInterface<?   extends CharSequence>)emptyInterface</warning>;
    //unchecked
    ChildUnaryGenericInterface<? extends CharSequence > t3 = <warning descr="Unchecked cast: 'BiGenericInterface<capture<? extends java.lang.String>,capture<? extends java.lang.String>>' to 'ChildUnaryGenericInterface<? extends java.lang.CharSequence>'">(ChildUnaryGenericInterface<? extends CharSequence>)t</warning>;
    StringOneGenericInterface<? extends CharSequence > t4 = (StringOneGenericInterface<? extends CharSequence>)stringI;
    UnaryGenericInterface<? extends CharSequence > t5 = (UnaryGenericInterface<? extends CharSequence>)stringI;
    UnaryGenericInterface<String> t6 = (UnaryGenericInterface<String>)stringI;
    StringInterface t7 = (StringInterface) stringI;
    ChildUnaryGenericInterfaceWithBound<? extends CharSequence> childChild2 = (ChildUnaryGenericInterfaceWithBound<? extends CharSequence>)t;
    BoundChildBiGenericInterface<? extends CharSequence> child3 = (BoundChildBiGenericInterface<? extends CharSequence>)t;
    TwoPathGenericChild<? extends CharSequence> child1Child3 = (TwoPathGenericChild<? extends CharSequence>)t;
  }
}

interface EmptyInterface {}
interface BiGenericInterface<T, R> extends EmptyInterface {
}

interface UnaryGenericInterface<T> extends BiGenericInterface<T, T> {}

interface StringOneGenericInterface<T> extends BiGenericInterface<T, String> { }
interface StringInterface extends BiGenericInterface<String, String> { }

interface ChildUnaryGenericInterface<T> extends UnaryGenericInterface<T> {}

interface ChildUnaryGenericInterfaceWithBound<T extends CharSequence> extends UnaryGenericInterface<T> { }

interface BoundChildBiGenericInterface<T extends CharSequence> extends BiGenericInterface<T, T> {}

interface TwoPathGenericChild<T extends CharSequence> extends UnaryGenericInterface<T>, BiGenericInterface<T, T> {}
