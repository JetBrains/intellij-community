package com.siyeh.igtest.encapsulation;

import java.util.*;
import java.util.stream.*;
import com.google.common.collect.*;

public class AssignmentOrReturnOfFieldWithMutableType
{
    private Set m_foo;
    private List<String> m_fooBar;
    private int[] m_bar;

    public AssignmentOrReturnOfFieldWithMutableType(List<String> fooBar)
    {
        m_fooBar = <warning descr="Assignment to List<String> field 'm_fooBar' from parameter 'fooBar'">fooBar</warning>;
    }

    public AssignmentOrReturnOfFieldWithMutableType(Set foo)
    {
        m_foo = <warning descr="Assignment to Set field 'm_foo' from parameter 'foo'">foo</warning>;
    }

    public AssignmentOrReturnOfFieldWithMutableType(int[] bar)
    {
        m_bar = <warning descr="Assignment to int[] field 'm_bar' from parameter 'bar'">bar</warning>;
    }

    private class HH {
        private String[] ss;

        HH(String[] ss){
            (this.ss)  = (ss);
        }
    }
}
class AssignmentToDateFieldFromParameterInspection
{
    private Date m_foo;
    private Calendar m_bar;

    public static void main(String[] args)
    {
        new AssignmentToDateFieldFromParameterInspection(new Date());
    }

    public AssignmentToDateFieldFromParameterInspection(Date foo)
    {
        m_foo = <warning descr="Assignment to Date field 'm_foo' from parameter 'foo'">foo</warning>;
        Date bar;
        bar = foo;
    }

    public AssignmentToDateFieldFromParameterInspection(Calendar bar)
    {
        m_bar = <warning descr="Assignment to Calendar field 'm_bar' from parameter 'bar'">bar</warning>;
    }
}
class ReturnOfCollectionFieldInspection
{
    private Set m_foo;
    private List<String> m_fooBar;
    private int[] m_bar;

    public ReturnOfCollectionFieldInspection(Set foo)
    {
        m_foo = Collections.unmodifiableSet(foo);
    }

    public Set foo()
    {
        return <warning descr="Return of Set field 'm_foo'">m_foo</warning>;
    }

    public List<String> fooBar()
    {
        return <warning descr="Return of List<String> field 'm_fooBar'">m_fooBar</warning>;
    }

    public List<String> fooBarEmpty()
    {
        return Collections.emptyList();
    }

    public int[] bar()
    {
        return <warning descr="Return of int[] field 'm_bar'">m_bar</warning>;
    }
}
class ReturnOfDateField
{
    private Date m_foo;
    //   private List<String> m_fooBar;
    private Calendar m_bar;

    public static void main(String[] args)
    {
        new ReturnOfDateField(new Date());
    }

    public ReturnOfDateField(Date foo)
    {
        m_foo = new Date(foo.getTime());
    }

    public Date foo()
    {
        return <warning descr="Return of Date field 'm_foo'">m_foo</warning>;
    }
/*

    public List<String> fooBar()
    {
        return m_fooBar;
    }
*/

    public Calendar bar()
    {
        return <warning descr="Return of Calendar field 'm_bar'">m_bar</warning>;
    }

    private Date hidden() {
        return m_foo;
    }

    interface A {
        Date d();
    }

    private void p(){
        A a = () -> {
            return <warning descr="Return of Date field 'm_foo'">m_foo</warning>;
        };
    }
}
class Test {
    Set field;

    interface I {
        Set m();
    }

    private void foo() {
        I i = () -> {
            return <warning descr="Return of Set field 'field'">field</warning>;
        };
    }
}

class ImmutableTest {
    final List<String> list = Collections.unmodifiableList(Arrays.asList("foo", "bar", "baz"));

    public List<String> getList() {
        return list;
    }
}

class GuavaTest {
    private final ImmutableList<?> list = ImmutableList.of();

    public ImmutableList<?> getList() {
        return list;
    }
}
class ReturnImmutableCollection {
    private final List<String> names;

    public ReturnImmutableCollection(List<String> names) {
        this.names = ImmutableList.copyOf(names);
    }

    public List<String> getNames() {
        return names;
    }
}

class NoArrayInitializer {
    static final String[] EMPTY;
    static {
        EMPTY = new String[0];
    }

    String[] getStr() {
        return EMPTY;
    }
}
// IDEA-265286
class Demo {
  public enum Foo {
    ONE,
    TWO,
    THEE
  }

  private static Foo fooOne;
  private static final List<Foo> LIST_ONE = List.of(Foo.TWO, Foo.THEE);
  private static final List<Foo> LIST_TWO = Stream.of(Foo.ONE, Foo.THEE).collect(Collectors.toUnmodifiableList());

  public static List<Foo> getListOne() {
    return LIST_ONE;
  }

  public static List<Foo> getListTwo() {
    return LIST_TWO;
  }

  private record Data3(Collection<String> strings) {}
}
record Data1(Collection<String> <warning descr="Implicit assignment and return of Collection<String> record component 'strings'">strings</warning>) {}
record Data2(Collection<String> <warning descr="Implicit assignment of Collection<String> record component 'strings'">strings</warning>) {
  @Override
  public Collection<String> strings() {
    return <warning descr="Return of Collection<String> field 'strings'">strings</warning>;
  }
}
record Data3(Collection<String> <warning descr="Implicit return of Collection<String> record component 'strings'">strings</warning>) {
  Data3(Collection<String> strings) {
      this.strings = <warning descr="Assignment to Collection<String> field 'strings' from parameter 'strings'">strings</warning>;
  }
}
record Data4(Collection<String> strings) {
  Data4(Collection<String> strings) {
      this.strings = <warning descr="Assignment to Collection<String> field 'strings' from parameter 'strings'">strings</warning>;
  }
  @Override
  public Collection<String> strings() {
    return <warning descr="Return of Collection<String> field 'strings'">strings</warning>;
  }
}